package io.syspulse.skel.ai.mcp

import scala.concurrent.{ExecutionContext, Future}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid.UUID

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors
import akka.Done
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.SourceQueueWithComplete
import spray.json._

import java.util.concurrent.ConcurrentHashMap

import io.syspulse.skel.service.{CommonRoutes, Routeable}

// ── JSON models ────────────────────────────────────────────────────────────────
case class JsonRpcRequest(
  jsonrpc: String,
  id: Option[JsValue],
  method: String,
  params: Option[JsValue]
)

case class JsonRpcResponse(
  jsonrpc: String = "2.0",
  id: Option[JsValue],
  result: Option[JsValue] = None,
  error: Option[JsValue]  = None
)

// ── Spray JSON formats ─────────────────────────────────────────────────────────

object McpJsonProtocol extends DefaultJsonProtocol {
  implicit val requestFmt: RootJsonFormat[JsonRpcRequest]   = jsonFormat4(JsonRpcRequest)
  implicit val responseFmt: RootJsonFormat[JsonRpcResponse] = jsonFormat4(JsonRpcResponse)
  implicit val errorFmt: RootJsonFormat[JsonRpcError]       = jsonFormat2(JsonRpcError)
}

import McpJsonProtocol._

// ── MCP Request handler ────────────────────────────────────────────────────────

private class McpRpcHandler(config: Config, tools: Seq[McpTool]) {

  private val serverInfo: JsValue = JsObject(
    "protocolVersion" -> JsString(config.protocolVersion),
    "serverInfo"      -> JsObject(
      "name"    -> JsString(config.serverName),
      "version" -> JsString(config.serverVersion)
    ),
    "capabilities" -> JsObject(
      "tools" -> JsObject()
    )
  )

  def handle(req: JsonRpcRequest): JsonRpcResponse =
    req.method match {

      case "initialize" =>
        JsonRpcResponse(id = req.id, result = Some(serverInfo))

      case "notifications/initialized" =>
        JsonRpcResponse(id = req.id, result = Some(JsObject()))

      case "tools/list" =>
        JsonRpcResponse(id = req.id, result = Some(McpTool.toolListJson(tools)))

      case "tools/call" =>
        val params   = req.params.getOrElse(JsObject()).asJsObject.fields
        val toolName = params.get("name").collect { case JsString(n) => n }.getOrElse("")
        val args     = params.getOrElse("arguments", JsObject())

        McpTool.callTool(tools, toolName, args) match {
          case Right(result) => JsonRpcResponse(id = req.id, result = Some(result))
          case Left(err)     =>
            JsonRpcResponse(
              id    = req.id,
              error = Some(JsObject("code" -> JsNumber(err.code), "message" -> JsString(err.message)))
            )
        }

      case unknown =>
        JsonRpcResponse(
          id    = req.id,
          error = Some(JsObject(
            "code"    -> JsNumber(-32601),
            "message" -> JsString(s"Method not found: $unknown")
          ))
        )
    }
}

// ── Shared SSE + JSON-RPC segment (used by [[McpRoutes]] and [[AppServer]]) ─

/** MCP streams at `…/{mcpBaseUri}/sse` and `…/message`. Used by [[McpRoutes]] and [[AppServer]]. */
private[mcp] class McpSseMessageRoutes(
  config: Config,
  tools: Seq[McpTool],
  /** Full path prefix for this MCP mount (no trailing slash), e.g. `/api/v1/mcp` or `/api/v1/server/mcp`. */
  mcpBaseUri: String
)(implicit context: ActorContext[_]) {

  private val log = Logger(getClass)

  private val sessions = new ConcurrentHashMap[String, SourceQueueWithComplete[ServerSentEvent]]()
  private val handler  = new McpRpcHandler(config, tools)

  implicit val system: akka.actor.typed.ActorSystem[_] = context.system
  implicit val mat: Materializer                       = Materializer(system)

  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  private def messagePath: String = mcpBaseUri.stripSuffix("/") + "/message"

  def routes: Route =
    concat(
      path("sse") {
        get {
          val sessionId = UUID.randomUUID().toString
          val (queue, source) =
            Source.queue[ServerSentEvent](bufferSize = 64, OverflowStrategy.dropHead)
              .preMaterialize()

          sessions.put(sessionId, queue)

          val endpointEvent = ServerSentEvent(
            data      = s"""$messagePath?sessionId=$sessionId""",
            eventType = Some("endpoint")
          )
          queue.offer(endpointEvent)

          complete(source)
        }
      },
      path("message") {
        post {
          parameter("sessionId") { sessionId =>
            entity(as[JsonRpcRequest]) { req =>
              val response     = handler.handle(req)
              val responseJson = response.toJson.compactPrint

              Option(sessions.get(sessionId)) match {
                case Some(queue) =>
                  queue.offer(ServerSentEvent(data = responseJson, eventType = Some("message")))
                case None =>
                  log.warn(s"SSE session not found: ${sessionId}")
              }

              complete(StatusCodes.Accepted)
            }
          }
        }
      }
    )
}

/** MCP only at [[Config.uri]] (e.g. `/api/v1/mcp/sse`, `/api/v1/mcp/message`) — `mcp` command. */
class McpRoutes(config: Config, tools: Seq[McpTool])(implicit context: ActorContext[_])
  extends CommonRoutes with Routeable {

  private val inner =
    new McpSseMessageRoutes(config, tools, config.uri.stripSuffix("/"))

  override def routes: Route = inner.routes
}

/**
 * Standalone HTTP server for tests and quick experiments: binds its own ActorSystem and listens on
 * [[Config.host]] / [[Config.port]] with routes at the URL root — `GET /mcp/sse`, `POST /mcp/message`,
 * `GET /health`. Production apps should use [[McpRoutes]] with [[io.syspulse.skel.Server.run]] instead.
 */
class McpServer(val config: Config, val tools: Seq[McpTool]) {

  private val sessions  = new ConcurrentHashMap[String, SourceQueueWithComplete[ServerSentEvent]]()
  private val handler   = new McpRpcHandler(config, tools)
  private var systemOpt: Option[ActorSystem[Nothing]] = None
  private var bindingOpt: Option[ServerBinding]       = None

  /** Actor system after [[start]] until [[stop]]; useful for tests. */
  def actorSystem: Option[ActorSystem[Nothing]] = systemOpt

  /** HTTP binding after a successful [[start]]. */
  def serverBinding: Option[ServerBinding] = bindingOpt

  import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._

  private def routes(implicit mat: Materializer, system: ActorSystem[Nothing]): Route =
    concat(
      pathPrefix("mcp") {
        concat(
          path("sse") {
            get {
              val sessionId = UUID.randomUUID().toString
              val (queue, source) =
                Source.queue[ServerSentEvent](bufferSize = 64, OverflowStrategy.dropHead)
                  .preMaterialize()

              sessions.put(sessionId, queue)

              val endpointEvent = ServerSentEvent(
                data      = s"""/mcp/message?sessionId=$sessionId""",
                eventType = Some("endpoint")
              )
              queue.offer(endpointEvent)

              complete(source)
            }
          },
          path("message") {
            post {
              parameter("sessionId") { sessionId =>
                entity(as[JsonRpcRequest]) { req =>
                  val response     = handler.handle(req)
                  val responseJson = response.toJson.compactPrint

                  Option(sessions.get(sessionId)) match {
                    case Some(queue) =>
                      queue.offer(ServerSentEvent(data = responseJson, eventType = Some("message")))
                    case None =>
                      println(s"[warn] No SSE session found for $sessionId")
                  }

                  complete(StatusCodes.Accepted)
                }
              }
            }
          }
        )
      },
      path("health") {
        get { complete("OK") }
      }
    )

  /** Binds HTTP; on failure the actor system is terminated. */
  def start(): Future[ServerBinding] = {
    val actorName = {
      val s = config.serverName.replaceAll("[^a-zA-Z0-9.-]", "-").take(48)
      if (s.nonEmpty) s"mcp-$s" else "mcp-server"
    }
    val sys = ActorSystem(Behaviors.empty, actorName)
    implicit val system: ActorSystem[Nothing] = sys
    implicit val mat: Materializer            = Materializer(system)
    implicit val ec: ExecutionContext         = system.executionContext

    systemOpt = Some(sys)

    Http()
      .newServerAt(config.host, config.port)
      .bind(routes)
      .recoverWith { case ex =>
        sys.terminate()
        systemOpt = None
        Future.failed(ex)
      }
      .map { binding =>
        bindingOpt = Some(binding)
        println(s"MCP standalone server at http://${binding.localAddress.getHostString}:${binding.localAddress.getPort}")
        println("Endpoints:")
        println("  GET  /mcp/sse            – open SSE stream")
        println("  POST /mcp/message        – send JSON-RPC messages")
        println("  GET  /health             – health check")
        binding
      }
  }

  /** Unbinds and terminates the actor system created by [[start]]. */
  def stop(): Future[Done] = {
    implicit val ec: ExecutionContext =
      systemOpt.map(_.executionContext).getOrElse(ExecutionContext.global)

    val unbind =
      bindingOpt
        .map(_.unbind())
        .getOrElse(Future.successful(Done))

    unbind.flatMap { _ =>
      systemOpt match {
        case Some(sys) =>
          systemOpt = None
          bindingOpt = None
          sys.terminate()
          sys.whenTerminated.map(_ => Done)
        case None =>
          Future.successful(Done)
      }
    }
  }

  /** Blocks until the server has been stopped (ActorSystem terminated). Call after [[start]]. */
  def runUntilShutdown(): Unit = {
    import scala.concurrent.Await
    import scala.concurrent.duration._
    implicit val ec: ExecutionContext =
      systemOpt.map(_.executionContext).getOrElse(ExecutionContext.global)

    systemOpt match {
      case Some(sys) =>
        Await.result(sys.whenTerminated, Duration.Inf)
      case None =>
        throw new IllegalStateException("runUntilShutdown requires a successful start()")
    }
  }
}

object McpServer {

  /** Default tool set used by the CLI. */
  def defaultTools: Seq[McpTool] =
    Seq(EchoMcpTool(), AddMcpTool())

  /** Standalone HTTP server (see [[McpServer]] class): `McpServer(config).start()`. */
  def apply(config: Config, tools: Seq[McpTool] = defaultTools): McpServer =
    new McpServer(config, tools)
}
