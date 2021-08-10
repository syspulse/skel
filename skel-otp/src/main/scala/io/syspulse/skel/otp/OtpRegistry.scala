package io.syspulse.skel.otp

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._

import ejisan.kuro.otp._

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.MetricName


final case class OtpCode(id:UUID,code: String)
final case class Otps(otps: immutable.Seq[Otp])

// create Otp Parameters
final case class OtpCreate(userId:UUID, secret: String,name:String, uri:String, period:Option[Int])
final case class OtpGenerate(userId:UUID, name:String, uri:String, period:Option[Int],digits:Option[Int],algo:Option[String])

object OtpRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetOtps(replyTo: ActorRef[Otps]) extends Command
  final case class GetOtp(id:UUID,replyTo: ActorRef[GetOtpResponse]) extends Command
  
  final case class GetOtpCode(id:UUID,replyTo: ActorRef[GetOtpCodeResponse]) extends Command
  final case class GetOtpCodeVerify(id:UUID,code:String,replyTo: ActorRef[GetOtpCodeVerifyResponse]) extends Command
  
  final case class CreateOtp(otpCreate: OtpCreate, replyTo: ActorRef[OtpCreatePerformed]) extends Command
  final case class DeleteOtp(id: UUID, replyTo: ActorRef[OtpActionPerformed]) extends Command
  final case class GenerateOtp(otpCreate: OtpCreate, replyTo: ActorRef[OtpCreatePerformed]) extends Command

  final case class GetUserOtps(userId:UUID,replyTo: ActorRef[Otps]) extends Command

  final case class GetOtpResponse(otp: Option[Otp])
  final case class GetOtpCodeResponse(code: Option[String])
  final case class GetOtpCodeVerifyResponse(code:String,authorized: Boolean)

  final case class OtpActionPerformed(description: String,id:Option[UUID])
  final case class OtpCreatePerformed(secret: String,id:Option[UUID])

  // this var reference is unfortunately needed for Metrics access
  var store: OtpStore = null //new OtpStoreDB //new OtpStoreCache

  def apply(store: OtpStore = new OtpStoreCache): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  override lazy val metricBaseName = MetricName("")
  metrics.gauge("otp-count") { store.size }

  def authCode(secret:String,period:Int, digits:Int=6):String = {
    val otpkey = OTPKey.fromBase32(secret.toUpperCase,false)
    val interval = period
    val totpSHA1 = TOTP(OTPAlgorithm.SHA1, digits, period, otpkey)
    val code = totpSHA1.generate()
    code
  }

  private def registry(store: OtpStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetOtps(replyTo) =>
        replyTo ! Otps(store.getAll)
        Behaviors.same

      case GetUserOtps(userId,replyTo) =>
        replyTo ! Otps(store.getForUser(userId))
        Behaviors.same

      case CreateOtp(otpCreate, replyTo) =>
        val id = UUID.randomUUID()

        val secret = if(otpCreate.secret.isBlank()) {
          val shaKey = OTPKey.random(OTPAlgorithm.SHA1)
          shaKey.toBase32.toLowerCase
        } else otpCreate.secret

        val otp = Otp(id,otpCreate.userId, secret, otpCreate.name, otpCreate.uri, otpCreate.period.getOrElse(30))
        val store1 = store.+(otp)

        replyTo ! OtpCreatePerformed(secret,Some(id))
        registry(store1.getOrElse(store))

      case GetOtp(id, replyTo) =>
        replyTo ! GetOtpResponse(store.get(id))
        Behaviors.same

      case GetOtpCode(id, replyTo) =>
        val otp = store.get(id)

        val code = otp.map( o => {
          authCode(o.secret,o.period)
        })
        replyTo ! GetOtpCodeResponse(code)
        Behaviors.same

      case GetOtpCodeVerify(id, codeUser, replyTo) =>
        val otp = store.get(id)
        
        val code = otp.map( o => {
          authCode(o.secret,o.period)
        })

        replyTo ! GetOtpCodeVerifyResponse(codeUser,(
          code.map( c => c == codeUser).getOrElse(false)
        ))
        Behaviors.same

      case DeleteOtp(id, replyTo) =>
        val store1 = store.-(id)
        replyTo ! OtpActionPerformed(s"deleted",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
