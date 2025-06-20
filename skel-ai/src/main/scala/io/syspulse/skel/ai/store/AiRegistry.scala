package io.syspulse.skel.ai.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await

import io.syspulse.skel.Command

import io.syspulse.skel.ai._
import io.syspulse.skel.ai.server._
import io.syspulse.skel.util.Util
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.Http
import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.util.ByteString

object AiRegistry {
  val log = Logger(s"${this}")
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  implicit val sys: ActorSystem = ActorSystem("AiRegistry")

  final case class GetAis(oid:Option[String],replyTo: ActorRef[Ais]) extends Command
  final case class GetAi(question:String,oid:Option[String], replyTo: ActorRef[Try[Ai]]) extends Command
  final case class GetAisBatch(question:Seq[String],oid:Option[String], replyTo: ActorRef[Seq[Ai]]) extends Command
  
  final case class CreateAi(oid:Option[String], req: AiCreateReq, replyTo: ActorRef[Try[Ai]]) extends Command
  final case class DeleteAi(question: String, oid:Option[String], replyTo: ActorRef[Try[Ai]]) extends Command

  final case class CreateAiStream(oid:Option[String], req: AiCreateReq, replyTo: ActorRef[Source[ServerSentEvent, Any]]) extends Command
  
  def apply(store: AiStore)(implicit config:Config): Behavior[io.syspulse.skel.Command] = {
    registry(store)(config)
  }

  private def registry(store: AiStore)(config:Config): Behavior[io.syspulse.skel.Command] = {    
        
    // Rules of oid
    // 1. if oid == None - this is generic Ai
    // 2. if oid == Some - this is specific Ai (e.g. "provider")
    Behaviors.receiveMessage {
      case GetAis(oid, replyTo) =>
        val all = store.all(oid)
        val filtered = if(oid==None) all else all.filter(o => o.oid == oid)
        replyTo ! Ais(
          filtered,
          total = Some(all.size)
        )
        Behaviors.same
      
      case GetAi(question0, oid, replyTo) =>
        val question = question0.toLowerCase()

        val o = for {
          o0 <- {
            store.???(question,oid)
          }
          b <- if(oid == None) Success(true) else Success(o0.oid == oid)
          o1 <- if(b) Success(o0) else Failure(new Exception(s"not found: ${question}"))
        } yield o1
        
        replyTo ! o

        Behaviors.same
      
      case CreateAi(oid, req, replyTo) =>
      
        val o = for {
          o1 <- store.????(req.question,req.model,oid)
          o2 <- store.+(o1)
        } yield o2
                
        replyTo ! o
        Behaviors.same
            
      case DeleteAi(question0, oid, replyTo) =>
        log.info(s"del: ${question0}, oid=${oid}")
        val question = question0.toLowerCase()

        val r = store.del(question,oid)
        Behaviors.same
      
      case CreateAiStream(oid, req, replyTo) =>
        // val method = "POST"                
        // val sseUrl = s"http://localhost:8300/sse"
        
        // val httpRequest = 
        //   HttpRequest(
        //     method = HttpMethods.POST,
        //     uri = sseUrl,
        //     entity = HttpEntity(ContentTypes.`application/json`, req.question)
        //   )
        
        // // Create a source that makes the HTTP request and parses SSE events directly
        // // This ensures that when the HTTP connection to HttpServerAkka ends, the stream to client also ends
        // val r = Source.future(Http()(sys).singleRequest(httpRequest))
        //   .flatMapConcat { response =>
        //     if (response.status == StatusCodes.OK) {
        //       response.entity.dataBytes
        //         .via(akka.stream.scaladsl.Framing.delimiter(ByteString("\n"), maximumFrameLength = 8192))
        //         .map(_.utf8String.trim)
        //         .filter(_.nonEmpty)
        //         .map { line =>
        //           // Parse SSE format: data: <json>
        //           if (line.startsWith("data: ")) {
        //             val data = line.substring(6)
        //             if (data.nonEmpty) {
        //               ServerSentEvent(data = data)
        //             } else {
        //               ServerSentEvent("", eventType = Some("done"))
        //             }
        //           } else if (line.startsWith("event: ")) {
        //             // Handle event type
        //             val eventType = line.substring(7)
        //             ServerSentEvent("", eventType = Some(eventType))
        //           } else if (line.startsWith("id: ")) {
        //             // Handle event id
        //             val id = line.substring(4)
        //             ServerSentEvent("", id = Some(id))
        //           } else {
        //             // Forward other lines as-is
        //             ServerSentEvent(data = line)
        //           }
        //         }
        //     } else {
        //       Source.single(ServerSentEvent(s"Error: ${response.status}", eventType = Some("error")))
        //     }
        //   }
        val r = store.getProvider(oid) match {
          case Some(p) => 
            p.askStream(
              Ai(
                question = req.question,
                model = req.model,
                oid = oid,
                xid = req.id
              ),
              instructions = req.instructions,
              tools = Seq.empty
            )
          case None => Source.failed(new Exception(s"Provider not found: ${oid}"))
        }

        replyTo ! r
        Behaviors.same
      
    }
        
  }

}
