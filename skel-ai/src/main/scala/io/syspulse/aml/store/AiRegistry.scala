package io.syspulse.ai.store

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

import io.syspulse.ai._
import io.syspulse.ai.server._
import io.syspulse.skel.util.Util

object AiRegistry {
  val log = Logger(s"${this}")
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    
  final case class GetAis(oid:Option[String],replyTo: ActorRef[Ais]) extends Command
  final case class GetAi(question:String,oid:Option[String], replyTo: ActorRef[Try[Ai]]) extends Command
  final case class GetAisBatch(question:Seq[String],oid:Option[String], replyTo: ActorRef[Seq[Ai]]) extends Command
  
  final case class CreateAi(oid:Option[String], req: AiCreateReq, replyTo: ActorRef[Try[Ai]]) extends Command
  final case class DeleteAi(question: String, oid:Option[String], replyTo: ActorRef[Try[Ai]]) extends Command
  
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
      
    }
        
  }

}
