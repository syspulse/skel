package io.syspulse.skel.auth

import io.jvm.uuid._

final case class Auth(uid: String, jwt:String, code: String, scope:String, age: Long = System.currentTimeMillis() + AuthCode.DEF_AGE, id: UUID = UUID.random)

object AuthCode {
  val DEF_AGE = 1000L * 60 * 60
}

