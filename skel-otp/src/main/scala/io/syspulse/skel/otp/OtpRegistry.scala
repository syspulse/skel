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
final case class OtpRandom(name:Option[String]=None, uri:Option[String]=None, period:Option[Int] = Some(30),digits:Option[Int] = Some(6),algo:Option[String] = None)

object OtpRegistry extends DefaultInstrumented  {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetOtps(replyTo: ActorRef[Otps]) extends Command
  final case class GetOtp(id:UUID,replyTo: ActorRef[GetOtpResponse]) extends Command
  
  final case class GetOtpCode(id:UUID,replyTo: ActorRef[GetOtpCodeResponse]) extends Command
  final case class GetOtpCodeVerify(id:UUID,code:String,replyTo: ActorRef[GetOtpCodeVerifyResponse]) extends Command
  
  final case class CreateOtp(otpCreate: OtpCreate, replyTo: ActorRef[OtpCreateResult]) extends Command
  final case class DeleteOtp(id: UUID, replyTo: ActorRef[OtpActionResult]) extends Command
  final case class RandomOtp(otpRandom: OtpRandom, replyTo: ActorRef[OtpRandomResult]) extends Command
  final case class RandomHtml(otpRandom: OtpRandom, replyTo: ActorRef[String]) extends Command

  final case class GetUserOtps(userId:UUID,replyTo: ActorRef[Otps]) extends Command

  final case class GetOtpResponse(otp: Option[Otp])
  final case class GetOtpCodeResponse(code: Option[String])
  final case class GetOtpCodeVerifyResponse(code:String,authorized: Boolean)

  final case class OtpActionResult(description: String,id:Option[UUID])
  final case class OtpCreateResult(secret: String,id:Option[UUID])
  final case class OtpRandomResult(secret: String,qrImage:String)

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

  def authRandom(name:String, uri:String = "",period:Int = 30,digits:Int = 6,algo:String = "SHA1"):String = {
    val shaKey = algo match {
      case "SHA1" => OTPKey.random(OTPAlgorithm.SHA1)
      case "SHA256" => OTPKey.random(OTPAlgorithm.SHA256)
      case "SHA512" => OTPKey.random(OTPAlgorithm.SHA512)
    }
    shaKey.toBase32.toLowerCase
  }

  def authQR (secret:String,name:String, uri:String = "",period:Int = 30,digits:Int = 6,algo:String = "SHA1"):String = {
    import net.glxn.qrgen.QRCode
    import net.glxn.qrgen.image.ImageType
    import java.util.Base64
    import java.net.URLEncoder
    import java.nio.charset.StandardCharsets

    val algorithm = algo
	  val account = name
	  val issuer = uri
    val image = ""
    val lock = "false"
    val otpType = "totp"
    
    var otpURI = "otpauth://" + otpType + "/";

	  if (issuer.length > 0)
		  otpURI = otpURI + URLEncoder.encode(issuer, StandardCharsets.UTF_8.toString) + ":"

    otpURI = otpURI + URLEncoder.encode(account, StandardCharsets.UTF_8.toString)
    otpURI = otpURI + URLEncoder.encode("?secret=" + secret, StandardCharsets.UTF_8.toString)
    otpURI = otpURI + URLEncoder.encode("&algorithm=" + algorithm, StandardCharsets.UTF_8.toString)
    otpURI = otpURI + URLEncoder.encode("&digits=" + digits, StandardCharsets.UTF_8.toString)
    otpURI = otpURI + URLEncoder.encode("&period=" + period, StandardCharsets.UTF_8.toString)
    otpURI = otpURI + URLEncoder.encode("&lock=" + lock, StandardCharsets.UTF_8.toString)

    val width = 512
    val height = 512
    val encodedBytes = Base64.getEncoder.encode(
        QRCode.from(otpURI).to(ImageType.PNG).withSize(width, height).withCharset("UTF-8").stream().toByteArray()
    )
    "data:image/gif;base64," + new String(encodedBytes)
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

        replyTo ! OtpCreateResult(secret,Some(id))
        registry(store1.getOrElse(store))

      case RandomOtp(otpRandom, replyTo) =>
        
        val secret = authRandom(otpRandom.name.getOrElse(""), otpRandom.uri.getOrElse(""), 
          otpRandom.period.getOrElse(30), otpRandom.digits.getOrElse(6), otpRandom.algo.getOrElse("SHA1")
        )

        val qrImage = authQR(secret,
          otpRandom.name.getOrElse(""), otpRandom.uri.getOrElse(""), 
          otpRandom.period.getOrElse(30), otpRandom.digits.getOrElse(6), otpRandom.algo.getOrElse("SHA1")
        )
        replyTo ! OtpRandomResult(secret,qrImage)
        Behaviors.same

      case RandomHtml(otpRandom, replyTo) =>
        
        val secret = authRandom(otpRandom.name.getOrElse(""), otpRandom.uri.getOrElse(""), 
          otpRandom.period.getOrElse(30), otpRandom.digits.getOrElse(6), otpRandom.algo.getOrElse("SHA1")
        )

        val qrImage = authQR(secret,
          otpRandom.name.getOrElse(""), otpRandom.uri.getOrElse(""), 
          otpRandom.period.getOrElse(30), otpRandom.digits.getOrElse(6), otpRandom.algo.getOrElse("SHA1")
        )

        val html:String = scala.io.Source.fromResource("qr-code.html").getLines().mkString("\n")
        val htmlOut = html.replaceAll("\\$QR_IMAGE",qrImage)

        replyTo ! htmlOut
        Behaviors.same

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
        replyTo ! OtpActionResult(s"deleted",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
