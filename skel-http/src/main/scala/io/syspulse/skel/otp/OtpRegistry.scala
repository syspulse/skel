package io.syspulse.skel.otp

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
//import java.util.UUID

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName


final case class OtpCode(id:UUID,code: String)
final case class Otps(otps: immutable.Seq[Otp])

// create Otp Parameters
final case class OtpCreate(secret: String,name:String, uri:String, period:Option[Int])

object OtpRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetOtps(replyTo: ActorRef[Otps]) extends Command
  final case class GetOtp(id:UUID,replyTo: ActorRef[GetOtpResponse]) extends Command
  final case class GetOtpCode(id:UUID,replyTo: ActorRef[GetOtpCodeResponse]) extends Command
  final case class CreateOtp(otpCreate: OtpCreate, replyTo: ActorRef[OtpActionPerformed]) extends Command
  final case class DeleteOtp(id: UUID, replyTo: ActorRef[OtpActionPerformed]) extends Command

  final case class GetOtpResponse(otp: Option[Otp])
  final case class GetOtpCodeResponse(otpCode: Option[OtpCode])
  final case class OtpActionPerformed(description: String,id:Option[UUID])

  // this var reference is unfortunately needed for Metrics access
  var store: OtpStore = null //new OtpStoreDB //new OtpStoreCache

  def apply(store: OtpStore = new OtpStoreCache): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("otp-count") { store.size }

  private def registry(store: OtpStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetOtps(replyTo) =>
        replyTo ! Otps(store.getAll)
        Behaviors.same
      case CreateOtp(otpCreate, replyTo) =>
        val id = UUID.randomUUID()
        val otp = Otp(id,otpCreate.secret,otpCreate.name,otpCreate.uri,otpCreate.period.getOrElse(30))
        val store1 = store.+(otp)
        replyTo ! OtpActionPerformed(s"created",Some(id))
        registry(store1.getOrElse(store))
      case GetOtp(id, replyTo) =>
        replyTo ! GetOtpResponse(store.get(id))
        Behaviors.same
      case DeleteOtp(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! OtpActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
