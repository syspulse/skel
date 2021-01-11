package io.syspulse.auth.otp

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
//import java.util.UUID

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName


final case class OtpCode(id:UUID,code: String)
final case class Otp(id:UUID, secret: String,name:String, uri:String, period:Int)
final case class Otps(otps: immutable.Seq[Otp])

// create Otp Parameters
final case class OtpCreate(secret: String,name:String, uri:String, period:Option[Int])

object OtpRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skeleton.Command

  final case class GetOtps(replyTo: ActorRef[Otps]) extends Command
  final case class GetOtp(id:UUID,replyTo: ActorRef[GetOtpResponse]) extends Command
  final case class GetOtpCode(id:UUID,replyTo: ActorRef[GetOtpCodeResponse]) extends Command
  final case class CreateOtp(otpCreate: OtpCreate, replyTo: ActorRef[OtpActionPerformed]) extends Command
  final case class DeleteOtp(id: UUID, replyTo: ActorRef[OtpActionPerformed]) extends Command

  final case class GetOtpResponse(otp: Option[Otp])
  final case class GetOtpCodeResponse(otpCode: Option[OtpCode])
  final case class OtpActionPerformed(description: String)

  // this var reference is unfortunately needed for Metrics access
  var store: OtpStore = new OtpStoreDB //new OtpStoreCache

  def apply(): Behavior[io.syspulse.skeleton.Command] = registry(store)

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("otp-count") { store.size }

  private def registry(store: OtpStore): Behavior[io.syspulse.skeleton.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetOtps(replyTo) =>
        replyTo ! Otps(store.getAll)
        Behaviors.same
      case CreateOtp(otpCreate, replyTo) =>
        val id = UUID.randomUUID()
        val otp = Otp(id,otpCreate.secret,otpCreate.name,otpCreate.uri,otpCreate.period.getOrElse(30))
        replyTo ! OtpActionPerformed(s"OTP: ${otp} created.")
        registry(store.+(otp))
      case GetOtp(id, replyTo) =>
        replyTo ! GetOtpResponse(store.get(id))
        Behaviors.same
      case DeleteOtp(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! OtpActionPerformed(s"OTP: ${id} deleted")
        registry(store1)
    }
  }
}
