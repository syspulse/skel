package io.syspulse.skel.ai.mcp

import akka.actor.typed.scaladsl.ActorContext
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import spray.json._

import io.syspulse.skel.service.{CommonRoutes, Routeable}

/**
 * HTTP routes for the default `server` command: MCP under `/…/mcp` and REST
 * `GET`/`POST` `/…/{id}` (relative to `http.uri`, default `/api/v1/server` → `/api/v1/server/{id}`).
 */
class AppServer(config: Config, tools: Seq[McpTool])(implicit context: ActorContext[_])
  extends CommonRoutes with Routeable {

  private val mcpBaseUri: String = config.uri.stripSuffix("/") + "/mcp"
  private val mcpInner = new McpSseMessageRoutes(config, tools, mcpBaseUri)

  override def routes: Route =
    concat(
      pathPrefix("mcp") {
        mcpInner.routes
      },
      pathPrefix(Segment) { id =>
        pathEndOrSingleSlash {
          get {
            complete(
              HttpEntity(
                ContentTypes.`application/json`,
                JsObject("id" -> JsString(id), "method" -> JsString("GET")).compactPrint
              )
            )
          } ~
          post {
            entity(as[String]) { body =>
              complete(
                HttpEntity(
                  ContentTypes.`application/json`,
                  JsObject(
                    "id"     -> JsString(id),
                    "method" -> JsString("POST"),
                    "body"   -> JsString(body)
                  ).compactPrint
                )
              )
            }
          }
        }
      }
    )
}
