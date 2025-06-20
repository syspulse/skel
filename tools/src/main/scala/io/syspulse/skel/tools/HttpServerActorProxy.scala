package io.syspulse.skel.tools

import scala.collection.immutable
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

import akka.actor.{Actor, ActorSystem, Props, ActorRef}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import akka.stream.scaladsl.{Source, Sink}
import akka.stream.{Materializer, OverflowStrategy}
import akka.util.ByteString
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling._
import akka.pattern.ask
import akka.util.Timeout

import upickle._
import os._

import scala.concurrent.Await
import scala.util.control.NonFatal

// Messages for the SSE Proxy Actor
// sealed trait SseProxyMessage
// case class SseRequest(requestData: Option[String], replyTo: ActorRef) extends SseProxyMessage
// case class SseEvent(event: ServerSentEvent) extends SseProxyMessage
// case object SseComplete extends SseProxyMessage
// case class SseStreamRequest(requestData: Option[String], replyTo: ActorRef) extends SseProxyMessage

// // SSE Proxy Actor that handles SSE stream generation
// class SseProxyActor(delay: Long) extends Actor {
//   import context.dispatcher
  
//   def receive: Receive = {
//     case SseRequest(requestData, replyTo) =>
//       // Direct actor communication for immediate responses
//       handleSseRequest(requestData, replyTo)
      
//     case SseStreamRequest(requestData, replyTo) =>
//       // Future-based communication for streaming responses
//       val future = Future {
//         generateSseStream(requestData)
//       }
      
//       future.onComplete {
//         case Success(stream) =>
//           // Forward the stream to the replyTo actor by running it
//           import akka.stream.scaladsl.Sink
//           import akka.stream.ActorMaterializer
//           implicit val materializer: ActorMaterializer = ActorMaterializer()(context.system)
          
//           stream.runWith(Sink.foreach { event =>
//             replyTo ! event
//           })
//           .onComplete {
//             case Success(_) =>
//               // Stream completed successfully
//               Console.err.println("SSE stream completed successfully")
//             case Failure(ex) =>
//               // Stream failed
//               Console.err.println(s"SSE stream failed: ${ex.getMessage}")
//               replyTo ! ServerSentEvent(s"Error: ${ex.getMessage}", eventType = Some("error"))
//           }(context.dispatcher)
//         case Failure(ex) =>
//           // Send error event
//           replyTo ! ServerSentEvent(s"Error: ${ex.getMessage}", eventType = Some("error"))
//       }
      
//     case SseEvent(event) =>
//       // Forward the event to the replyTo actor
//       sender() ! event
      
//     case SseComplete =>
//       // Send completion marker
//       sender() ! ServerSentEvent("", eventType = Some("done"))
//   }
  
//   private def handleSseRequest(requestData: Option[String], replyTo: ActorRef): Unit = {
//     // Make HTTP connection to HttpServerAkka SSE endpoint
//     val sseUrl = s"http://localhost:8300/sse"
    
//     val httpRequest = if (requestData.isDefined) {
//       HttpRequest(
//         method = HttpMethods.POST,
//         uri = sseUrl,
//         entity = HttpEntity(ContentTypes.`application/json`, requestData.get)
//       )
//     } else {
//       HttpRequest(
//         method = HttpMethods.GET,
//         uri = sseUrl
//       )
//     }
    
//     // Execute the HTTP request and forward SSE events
//     val responseFuture = Http()(context.system).singleRequest(httpRequest)
    
//     responseFuture.onComplete {
//       case Success(response) =>
//         if (response.status == StatusCodes.OK) {
//           // Parse SSE events from the response and forward them individually
//           import akka.stream.scaladsl.Sink
//           import akka.stream.ActorMaterializer
//           implicit val materializer: ActorMaterializer = ActorMaterializer()(context.system)
          
//           response.entity.dataBytes
//             .via(akka.stream.scaladsl.Framing.delimiter(ByteString("\n"), maximumFrameLength = 8192))
//             .map(_.utf8String.trim)
//             .filter(_.nonEmpty)
//             .runWith(Sink.foreach { line =>
//               // Parse SSE format: data: <json>
//               if (line.startsWith("data: ")) {
//                 val data = line.substring(6)
//                 if (data.nonEmpty) {
//                   // Forward the SSE event to the replyTo actor
//                   replyTo ! ServerSentEvent(data = data)
//                 }
//               } else if (line.startsWith("event: ")) {
//                 // Handle event type
//                 val eventType = line.substring(7)
//                 replyTo ! ServerSentEvent("", eventType = Some(eventType))
//               } else if (line.startsWith("id: ")) {
//                 // Handle event id
//                 val id = line.substring(4)
//                 replyTo ! ServerSentEvent("", id = Some(id))
//               } else {
//                 // Forward other lines as-is
//                 replyTo ! ServerSentEvent(data = line)
//               }
//             })
//             .onComplete {
//               case Success(_) =>
//                 // Stream completed successfully, send completion event
//                 replyTo ! ServerSentEvent("", eventType = Some("done"))
//                 // Close the actor reference to signal stream end
//                 context.system.stop(replyTo)
//               case Failure(ex) =>
//                 // Stream failed, send error event
//                 replyTo ! ServerSentEvent(s"Error: ${ex.getMessage}", eventType = Some("error"))
//                 context.system.stop(replyTo)
//             }(context.dispatcher)
//         } else {
//           replyTo ! ServerSentEvent(s"Error: ${response.status}", eventType = Some("error"))
//           context.system.stop(replyTo)
//         }
//       case Failure(ex) =>
//         replyTo ! ServerSentEvent(s"Error: ${ex.getMessage}", eventType = Some("error"))
//         context.system.stop(replyTo)
//     }(context.dispatcher)
//   }
  
//   private def generateSseStream(requestData: Option[String]): Source[ServerSentEvent, Any] = {
//     // Make HTTP connection to HttpServerAkka SSE endpoint
//     val sseUrl = s"http://localhost:8300/sse"
    
//     val httpRequest = if (requestData.isDefined) {
//       HttpRequest(
//         method = HttpMethods.POST,
//         uri = sseUrl,
//         entity = HttpEntity(ContentTypes.`application/json`, requestData.get)
//       )
//     } else {
//       HttpRequest(
//         method = HttpMethods.GET,
//         uri = sseUrl
//       )
//     }
    
//     // Create a source that makes the HTTP request and parses SSE events
//     import akka.stream.ActorMaterializer
//     implicit val materializer: ActorMaterializer = ActorMaterializer()(context.system)
    
//     Source.future(Http()(context.system).singleRequest(httpRequest))
//       .flatMapConcat { response =>
//         if (response.status == StatusCodes.OK) {
//           response.entity.dataBytes
//             .via(akka.stream.scaladsl.Framing.delimiter(ByteString("\n"), maximumFrameLength = 8192))
//             .map(_.utf8String.trim)
//             .filter(_.nonEmpty)
//             .map { line =>
//               // Parse SSE format: data: <json>
//               if (line.startsWith("data: ")) {
//                 val data = line.substring(6)
//                 if (data.nonEmpty) {
//                   ServerSentEvent(data = data)
//                 } else {
//                   ServerSentEvent("", eventType = Some("done"))
//                 }
//               } else if (line.startsWith("event: ")) {
//                 // Handle event type
//                 val eventType = line.substring(7)
//                 ServerSentEvent("", eventType = Some(eventType))
//               } else if (line.startsWith("id: ")) {
//                 // Handle event id
//                 val id = line.substring(4)
//                 ServerSentEvent("", id = Some(id))
//               } else {
//                 // Forward other lines as-is
//                 ServerSentEvent(data = line)
//               }
//             }
//             .concat(Source.single(ServerSentEvent("", eventType = Some("done")))) // Ensure completion
//         } else {
//           Source.single(ServerSentEvent(s"Error: ${response.status}", eventType = Some("error")))
//             .concat(Source.single(ServerSentEvent("", eventType = Some("done")))) // Ensure completion
//         }
//       }
//   }
  

// }

abstract class HttpServerActorProxy {
  
  implicit val system: ActorSystem = ActorSystem("HttpServerActorProxy")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher
  implicit val timeout: Timeout = Timeout(30.seconds)

  val port = sys.env.get("PORT").getOrElse("8301").toInt
  val host = sys.env.get("HOST").getOrElse("0.0.0.0")
  val url = sys.env.get("HOST").getOrElse("/api/v1/tools")
  val delay = sys.env.get("DELAY").map(_.toLong).getOrElse(250L)

  var requests: Seq[() => String] = Seq()
  var current = 0
  
  // Create the SSE Proxy Actor
//   val sseProxyActor: ActorRef = system.actorOf(Props(new SseProxyActor(delay)), "sse-proxy")

  def initialize(): Unit = {}

  def run(args0: Array[String]): Unit = {
    if (args0.size > 0) Console.err.println(s"${args0.mkString(",")}")

    val args = 
      if (args0.size > 0)
        if (args0(0).size == 0)
          args0.tail
        else
          args0
      else
        args0

    val reqs = args.toList match {            
      case Nil => 
        Seq(() => s"""{"ts": ${System.currentTimeMillis()}, "status": 100}""")
      case reqs =>         
        reqs.map(f => f.split("://").toList match {
          case "file" :: file :: Nil =>
            () => os.read(os.Path(f, os.pwd))
          case rsp :: Nil => () => rsp
        })
    }

    this.requests = reqs

    val route = createRoute()
    
    Console.err.println(s"Listening on ${host}:${port}...")
    
    val bindingFuture = Http().newServerAt(host, port).bind(route)
    
    bindingFuture.onComplete {
      case Success(binding) =>
        Console.err.println(s"Server online at http://${host}:${port}/")
      case Failure(ex) =>
        Console.err.println(s"Failed to bind to ${host}:${port}: ${ex.getMessage}")
        system.terminate()
    }

    // Keep the server running
    Await.result(system.whenTerminated, Duration.Inf)
  }

  def createRoute(): Route = {
    val corsSettings = corsSettingsFromConfig
    
    cors(corsSettings) {
      concat(
        // Root endpoints
        pathEndOrSingleSlash {
          concat(
            get {
              Console.err.println(s"<- GET")
              if (current >= requests.size) current = 0
              val rsp = requests(current)()
              Console.err.println(s"[${rsp}] -> ")
              current = current + 1
              complete(HttpEntity(ContentTypes.`application/json`, rsp))
            },
            post {
              entity(as[String]) { body =>
                Console.err.println(s"<<< POST")
                Console.err.println(s"<<< Body:\n")
                Console.println(body)
                if (current >= requests.size) current = 0
                val rsp = requests(current)()
                Console.err.println(s"[${rsp}] -> ")
                current = current + 1
                complete(HttpEntity(ContentTypes.`application/json`, rsp))
              }
            }
          )
        },
        
        // Custom URL endpoint
        path(url.stripPrefix("/")) {
          concat(
            post {
              entity(as[String]) { body =>
                Console.err.println(s"<<< POST")
                Console.err.println(s"<<< Body:\n")
                Console.println(body)
                if (current >= requests.size) current = 0
                val rsp = requests(current)()
                Console.err.println(s"[${rsp}] -> ")
                current = current + 1
                complete(HttpEntity(ContentTypes.`application/json`, rsp))
              }
            },
            options {
              Console.err.println(s"<<< OPTIONS")
              complete(StatusCodes.OK)
            }
          )
        },
        
        // SSE endpoints with Actor proxy
        path("sse") {
          concat(
            get {
              Console.err.println(s"<- GET SSE")
              complete(createSseStream(None))
            },
            post {
              entity(as[String]) { requestData =>
                Console.err.println(s"<<< POST SSE")
                Console.err.println(s"<<< Body:\n")
                Console.println(requestData)
                complete(createSseStream(Some(requestData)))
              }
            }
          )
        },
        
        // Static files
        pathPrefix("web") {
          getFromDirectory("web")
        }
      )
    }
  }

  private def createSseStream(requestData: Option[String]): Source[ServerSentEvent, Any] = {
    val method = if (requestData.isDefined) "POST" else "GET"
    Console.err.println(s"<- $method SSE STREAM")
    
    // Make HTTP connection to HttpServerAkka SSE endpoint directly
    val sseUrl = s"http://localhost:8300/sse"
    
    val httpRequest = if (requestData.isDefined) {
      HttpRequest(
        method = HttpMethods.POST,
        uri = sseUrl,
        entity = HttpEntity(ContentTypes.`application/json`, requestData.get)
      )
    } else {
      HttpRequest(
        method = HttpMethods.GET,
        uri = sseUrl
      )
    }
    
    // Create a source that makes the HTTP request and parses SSE events directly
    // This ensures that when the HTTP connection to HttpServerAkka ends, the stream to client also ends
    Source.future(Http()(system).singleRequest(httpRequest))
      .flatMapConcat { response =>
        if (response.status == StatusCodes.OK) {
          response.entity.dataBytes
            .via(akka.stream.scaladsl.Framing.delimiter(ByteString("\n"), maximumFrameLength = 8192))
            .map(_.utf8String.trim)
            .filter(_.nonEmpty)
            .map { line =>
              // Parse SSE format: data: <json>
              if (line.startsWith("data: ")) {
                val data = line.substring(6)
                if (data.nonEmpty) {
                  ServerSentEvent(data = data)
                } else {
                  ServerSentEvent("", eventType = Some("done"))
                }
              } else if (line.startsWith("event: ")) {
                // Handle event type
                val eventType = line.substring(7)
                ServerSentEvent("", eventType = Some(eventType))
              } else if (line.startsWith("id: ")) {
                // Handle event id
                val id = line.substring(4)
                ServerSentEvent("", id = Some(id))
              } else {
                // Forward other lines as-is
                ServerSentEvent(data = line)
              }
            }
        } else {
          Source.single(ServerSentEvent(s"Error: ${response.status}", eventType = Some("error")))
        }
      }
  }

  private def corsSettingsFromConfig = {
    import akka.http.scaladsl.model.HttpMethods._
    
    CorsSettings.defaultSettings
      .withAllowCredentials(false)
      .withAllowedMethods(Seq(GET, POST, OPTIONS))
  }
}

object HttpServerActorProxy extends HttpServerActorProxy {
  def main(args: Array[String]): Unit = {
    try {
      initialize()
      run(args)
    } catch {
      case NonFatal(e) =>
        Console.err.println(s"Server failed: ${e.getMessage}")
        e.printStackTrace()
        system.terminate()
    }
  }
} 