package io.syspulse.skel.tools

import scala.collection.immutable
import java.util.concurrent.TimeUnit
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Try, Success, Failure}

import akka.actor.ActorSystem
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

import upickle._
import os._

import scala.concurrent.Await
import scala.util.control.NonFatal

abstract class HttpServerAkka {
  
  implicit val system: ActorSystem = ActorSystem("HttpServerAkka")
  implicit val materializer: Materializer = Materializer(system)
  implicit val ec: ExecutionContext = system.dispatcher

  val port = sys.env.get("PORT").getOrElse("8300").toInt
  val host = sys.env.get("HOST").getOrElse("0.0.0.0")
  val url = sys.env.get("HOST").getOrElse("/api/v1/tools")
  val delay = sys.env.get("DELAY").map(_.toLong).getOrElse(250L)

  var requests: Seq[() => String] = Seq()
  var current = 0

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
        
        // SSE endpoints
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

  def createSseStream(requestData: Option[String]): Source[ServerSentEvent, Any] = {
    val isPost = requestData.isDefined
    val method = if (isPost) "POST" else "GET"
    Console.err.println(s"<- $method SSE STREAM")
    
    val streamId = s"stream-${System.currentTimeMillis()}"
    val model = "gpt-4-streaming"
    val created = System.currentTimeMillis() / 1000
    
    val steps = if (isPost) {
      val data = requestData.get
      Seq(
        ("received", s"Request received: $data"),
        ("validating", "Validating request"),
        ("processing", "Processing request"),
        ("finalizing", "Finalizing response"),
        ("completed", "Request completed successfully")
      )
    } else {
      Seq(
        ("initializing", "Stream started"),
        ("processing", "Processing step 1"),
        ("processing", "Processing step 2"), 
        ("processing", "Processing step 3"),
        ("completed", "Stream completed")
      )
    }
    
    // Create a proper streaming source with delays
    Source(steps.zipWithIndex)
      .map { case ((state, message), index) =>
        val isLast = index == steps.length - 1
        val json = if (isLast) {
          s"""{"id":"$streamId","object":"stream.chunk","created":$created,"model":"$model","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}"""
        } else {
          s"""{"id":"$streamId","object":"stream.chunk","created":$created,"model":"$model","choices":[{"index":0,"delta":{"content":"$state: $message"},"finish_reason":null}]}"""
        }
        
        Console.err.println(s"<- $method SSE: ${index + 1}/${steps.length} - $state: $message")
        
        ServerSentEvent(
          data = json,
          eventType = Some("chunk"),
          id = Some(index.toString)
        )
      }
      .throttle(1, delay.milliseconds) // Proper delay between events
      .concat(Source.single(ServerSentEvent("", eventType = Some("done")))) // End marker
  }

  private def corsSettingsFromConfig = {
    import akka.http.scaladsl.model.HttpMethods._
    
    CorsSettings.defaultSettings
      .withAllowCredentials(false)
      .withAllowedMethods(Seq(GET, POST, OPTIONS))
  }
}

object HttpServerAkka extends HttpServerAkka {
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