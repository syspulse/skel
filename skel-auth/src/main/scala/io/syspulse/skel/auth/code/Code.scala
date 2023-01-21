package io.syspulse.skel.auth.code

import scala.collection.immutable
import io.jvm.uuid._

final case class Code(authCode:String, xid:Option[String], accessToken:Option[String], expire: Long)
final case class Codes(codes: immutable.Seq[Code])

final case class CodeRes(code: Option[Code])
final case class CodeCreateRes(code: Code)
final case class CodeActionRes(status: String,code:Option[String])

object Code {
  val DEF_AGE = 60 // seconds short live span

  def apply(authCode:String, xid:Option[String] = None, accessToken:Option[String] = None, age: Long = DEF_AGE):Code = {
    new Code(authCode, xid, accessToken, System.currentTimeMillis + age * 1000L)
  }
}

