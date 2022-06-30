package io.syspulse.skel.otp

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import ejisan.kuro.otp._

import io.syspulse.skel.Command
// create Otp Parameters

object OtpRegistry {
  val log = Logger(s"${this}")
  
  final case class GetOtps(replyTo: ActorRef[Otps]) extends Command
  final case class GetOtp(id:UUID,replyTo: ActorRef[OtpRes]) extends Command
  
  final case class GetOtpCode(id:UUID,replyTo: ActorRef[OtpCodeRes]) extends Command
  final case class GetOtpCodeVerify(id:UUID,code:String,replyTo: ActorRef[OtpCodeVerifyRes]) extends Command
  
  final case class CreateOtp(otpCreate: OtpCreateReq, replyTo: ActorRef[OtpCreateRes]) extends Command
  final case class DeleteOtp(id: UUID, replyTo: ActorRef[OtpActionRes]) extends Command
  final case class RandomOtp(otpRandom: OtpRandomReq, replyTo: ActorRef[OtpRandomRes]) extends Command
  final case class RandomHtml(otpRandom: OtpRandomReq, replyTo: ActorRef[String]) extends Command

  final case class GetUserOtps(userId:UUID,replyTo: ActorRef[Otps]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: OtpStore = null //new OtpStoreDB //new OtpStoreCache

  def apply(store: OtpStore = new OtpStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  def authCode(secret:String,period:Int, digits:Int=6):String = {
    val otpkey = OTPKey.fromBase32(secret.toUpperCase,false)
    val interval = period
    val totpSHA1 = TOTP(OTPAlgorithm.SHA1, digits, period, otpkey)
    val code = totpSHA1.generate()
    code
  }

  def authRandom(algo:String = "SHA1"):String = {
    val shaKey = algo match {
      case "SHA1" => OTPKey.random(OTPAlgorithm.SHA1)
      case "SHA256" => OTPKey.random(OTPAlgorithm.SHA256)
      case "SHA512" => OTPKey.random(OTPAlgorithm.SHA512)
    }
    shaKey.toBase32.toLowerCase
  }

  def authQR (secret:String,name:String,account:String="",issuer:String = "",period:Int = 30,digits:Int = 6,algo:String = "SHA1"):String = {
    import net.glxn.qrgen.QRCode
    import net.glxn.qrgen.image.ImageType
    import java.util.Base64
    import java.net.URLEncoder
    import java.nio.charset.StandardCharsets

	  val image = ""
    val algorithm = algo
    val lock = "false"
    val otpType = "totp"
    
    var otpURI = "otpauth://" + otpType + "/";

	  if (name.size > 0)
		  otpURI = otpURI + URLEncoder.encode(name, StandardCharsets.UTF_8.toString) + ":"
    
    otpURI = otpURI + URLEncoder.encode(account, StandardCharsets.UTF_8.toString)

    otpURI = otpURI + "?secret=" + secret.toUpperCase()
    if (issuer.size > 0)
		  otpURI = otpURI + "&issuer=" + URLEncoder.encode(issuer, StandardCharsets.UTF_8.toString)

    otpURI = otpURI + "&algorithm=" + algorithm
    otpURI = otpURI + "&digits=" + digits
    otpURI = otpURI + "&period=" + period
    //otpURI = otpURI + "&lock=" + lock

    if (otpType == "hotp")
		  otpURI = otpURI + "&counter=0"

    log.info(s"otpURI: '${otpURI}")

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
        replyTo ! Otps(store.all)
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

        val otp = Otp(id,otpCreate.userId, secret, otpCreate.name, otpCreate.account, otpCreate.issuer.getOrElse(""), otpCreate.period.getOrElse(30))
        val store1 = store.+(otp)

        replyTo ! OtpCreateRes(secret,Some(id))
        registry(store1.getOrElse(store))

      case RandomOtp(otpRandom, replyTo) =>
        
        val secret = authRandom(otpRandom.algo.getOrElse("SHA1"))

        val qrImage = authQR(secret,
          otpRandom.name.getOrElse(""), otpRandom.account.getOrElse(""), otpRandom.issuer.getOrElse(""), 
          otpRandom.period.getOrElse(30), otpRandom.digits.getOrElse(6), otpRandom.algo.getOrElse("SHA1")
        )
        replyTo ! OtpRandomRes(secret,qrImage)
        Behaviors.same

      case RandomHtml(otpRandom, replyTo) =>
        
        val secret = authRandom(otpRandom.algo.getOrElse("SHA1"))

        val qrImage = authQR(secret,
          otpRandom.name.getOrElse(""), otpRandom.account.getOrElse(""), otpRandom.issuer.getOrElse(""), 
          otpRandom.period.getOrElse(30), otpRandom.digits.getOrElse(6), otpRandom.algo.getOrElse("SHA1")
        )

        val html:String = scala.io.Source.fromResource("qr-code.html").getLines().mkString("\n")
        val htmlOut = html
          .replaceAll("\\$OTP_SECRET",secret)
          .replaceAll("\\$OTP_NAME",otpRandom.name.getOrElse(""))
          .replaceAll("\\$OTP_ACCOUNT",otpRandom.account.getOrElse(""))
          .replaceAll("\\$OTP_ISSUER",otpRandom.issuer.getOrElse(""))
          .replaceAll("\\$OTP_TYPE","totp")
          .replaceAll("\\$OTP_PERIOD",otpRandom.period.getOrElse(30).toString)
          .replaceAll("\\$OTP_DIGITS",otpRandom.digits.getOrElse(6).toString)
          .replaceAll("\\$OTP_ALGO",otpRandom.algo.getOrElse("SHA1"))
          .replaceAll("\\$QR_IMAGE",qrImage)

        replyTo ! htmlOut
        Behaviors.same

      case GetOtp(id, replyTo) =>
        replyTo ! OtpRes(store.?(id))
        Behaviors.same

      case GetOtpCode(id, replyTo) =>
        val otp = store.?(id)

        val code = otp.map( o => {
          authCode(o.secret,o.period)
        })
        replyTo ! OtpCodeRes(code)
        Behaviors.same

      case GetOtpCodeVerify(id, codeUser, replyTo) =>
        val otp = store.?(id)
        
        val code = otp.map( o => {
          authCode(o.secret,o.period)
        })

        replyTo ! OtpCodeVerifyRes(codeUser,(
          code.map( c => c == codeUser).getOrElse(false)
        ))
        Behaviors.same

      case DeleteOtp(id, replyTo) =>
        val store1 = store.del(id)
        replyTo ! OtpActionRes(s"Success",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
