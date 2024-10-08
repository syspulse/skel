package io.syspulse.skel.telemetry.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.telemetry._
import io.syspulse.skel.telemetry.Telemetry.ID
import io.syspulse.skel.telemetry.server._
import scala.util.Try

object TelemetryRegistry {
  val log = Logger(s"${this}")
  
  final case class GetTelemetrys(replyTo: ActorRef[Telemetrys]) extends Command
  final case class GetTelemetry(id:Telemetry.ID,ts0:Long, ts1:Long, replyTo: ActorRef[Telemetrys]) extends Command
  final case class GetTelemetryOp(id:Telemetry.ID,ts0:Long, ts1:Long, op:Option[String], replyTo: ActorRef[Option[Telemetry]]) extends Command
  final case class GetTelemetryLast(id:Telemetry.ID,replyTo: ActorRef[Try[Telemetry]]) extends Command
  final case class SearchTelemetry(txt:String,ts0:Long, ts1:Long, replyTo: ActorRef[Telemetrys]) extends Command
  
  final case class CreateTelemetry(telemetryCreate: TelemetryCreateReq, replyTo: ActorRef[Telemetry]) extends Command
  final case class RandomTelemetry(replyTo: ActorRef[Telemetry]) extends Command

  final case class DeleteTelemetry(id: Telemetry.ID, replyTo: ActorRef[TelemetryActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: TelemetryStore = null //new TelemetryStoreDB //new TelemetryStoreCache

  def apply(store: TelemetryStore = new TelemetryStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: TelemetryStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetTelemetrys(replyTo) =>
        replyTo ! Telemetrys(store.all)
        Behaviors.same

      case GetTelemetry(id, ts0, ts1, replyTo) =>
        replyTo ! Telemetrys(store.?(id,ts0,ts1))
        Behaviors.same

      case GetTelemetryOp(id, ts0, ts1, op, replyTo) =>
        replyTo ! store.??(id,ts0,ts1,op)
        Behaviors.same

      case GetTelemetryLast(id, replyTo) =>
        replyTo ! store.last(id)
        Behaviors.same

      case SearchTelemetry(txt, ts0,ts1, replyTo) => 
        replyTo ! Telemetrys(store.??(txt,ts0,ts1))
        Behaviors.same

      case CreateTelemetry(telemetryCreate, replyTo) =>
        
        val telemetry = Telemetry(telemetryCreate.id, telemetryCreate.ts, telemetryCreate.data)
                
        val store1 = store.+(telemetry)

        replyTo ! telemetry
        Behaviors.same

      case RandomTelemetry(replyTo) =>
        
        //replyTo ! TelemetryRandomRes(secret,qrImage)
        Behaviors.same

      
      case DeleteTelemetry(vid, replyTo) =>
        val store1 = store.del(vid)
        replyTo ! TelemetryActionRes(s"Success",Some(vid.toString))
        Behaviors.same
    }
  }
}
