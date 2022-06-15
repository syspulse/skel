package io.syspulse.skel.auth.code

import io.jvm.uuid._

final case class Code(authCode:String, expire: Long)

object Code {
  val DEF_AGE = 60 // short live span

  def apply(authCode:String, age: Long = DEF_AGE):Code = {
    new Code(authCode, System.currentTimeMillis + age * 1000L)
  }
}

