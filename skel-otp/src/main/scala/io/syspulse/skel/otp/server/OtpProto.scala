package io.syspulse.skel.otp.server

import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.otp.Otp

final case class OtpCode(id:UUID, code: String)
final case class Otps(otps: immutable.Seq[Otp])

final case class OtpCreateReq(uid:UUID, secret: String, name:String, account:String, issuer:Option[String], period:Option[Int])
final case class OtpRandomReq(name: Option[String]=None, account:Option[String]=None, issuer:Option[String]=None, period:Option[Int] = Some(30),digits:Option[Int] = Some(6),algo:Option[String] = None)

final case class OtpActionRes(status: String,id:Option[UUID])
final case class OtpCreateRes(secret: String,id:Option[UUID])
final case class OtpRandomRes(secret: String,qrImage:String)

final case class OtpRes(otp: Option[Otp])
final case class OtpCodeRes(code: Option[String])
final case class OtpCodeVerifyRes(code:String,authorized: Boolean)