package io.syspulse.skel.otp

import scala.collection.immutable

import io.jvm.uuid._

final case class Otp(id:UUID, userId:UUID, secret: String,name:String, account:String, issuer:String="", period:Int = 30, digits:Int = 6,algo:String = "SHA1")

final case class OtpCode(id:UUID,code: String)
final case class Otps(otps: immutable.Seq[Otp])

final case class OtpCreate(userId:UUID, secret: String, name:String, account:String, issuer:Option[String], period:Option[Int])
final case class OtpRandom(name: Option[String]=None, account:Option[String]=None, issuer:Option[String]=None, period:Option[Int] = Some(30),digits:Option[Int] = Some(6),algo:Option[String] = None)
final case class OtpActionResult(description: String,id:Option[UUID])
final case class OtpCreateResult(secret: String,id:Option[UUID])
final case class OtpRandomResult(secret: String,qrImage:String)