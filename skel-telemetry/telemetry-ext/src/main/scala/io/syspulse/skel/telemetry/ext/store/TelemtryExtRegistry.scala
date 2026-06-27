package io.syspulse.skel.telemetry.ext.store

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future

import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.{Future,ExecutionContext,Await}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.Executors
import io.syspulse.skel.Command
import io.syspulse.skel.util.Util

import io.syspulse.skel.telemetry.ext._
import io.syspulse.skel.ErrNotFound

import io.syspulse.skel.telemetry.ext.Config

object TelemetryExtRegistry {
  val log = Logger(s"${this}")

  final case class GetTelemetry(key:Option[String], oid:Option[String], replyTo: ActorRef[Try[TelemetryChain]]) extends Command
  final case class SaveTelemetry(telemetry:TelemetryChain, oid:Option[String], replyTo: ActorRef[Try[TelemetryChain]]) extends Command  
  
  def apply(store: TelemetryExtStore)(implicit config:Config): Behavior[io.syspulse.skel.Command] = {
    registry(store)(config)
  }  

  private def registry(store: TelemetryExtStore)(config:Config): Behavior[io.syspulse.skel.Command] = {    
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threads))

    Behaviors.receiveMessage {      
      
      case GetTelemetry(key, oid, replyTo) =>
        val k = key.getOrElse(TelemetryExt.BLOCKCHAIN_KEY)
        store.???(k, oid).onComplete {
          case Success(r) =>
            replyTo ! Success(r)
          case Failure(e: ErrNotFound) =>
            log.info(s"${oid}/${k}: ${e.getMessage()}")
            replyTo ! Failure(e)
          case Failure(e) =>
            log.error(s"failed to get Telemetry: ${oid}/${k}: ${e.getMessage()}")
            replyTo ! Failure(e)
        }
        Behaviors.same

      case SaveTelemetry(telemetry, oid, replyTo) =>
        store.+(telemetry).onComplete(replyTo ! _)
        Behaviors.same
    }
        
  }

}
