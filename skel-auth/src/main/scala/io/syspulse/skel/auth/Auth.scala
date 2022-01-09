package io.syspulse.skel.auth

import io.jvm.uuid._

final case class Auth(auid: String, accessToken:String, idToken:String, scope:String, expire: Long, id: UUID)

object Auth {
  val DEF_AGE = 60 * 60 // seconds

  // accessToken cannot be here for prod. It is for RnD purposes here and will be removed
  // ATTENTION: age must be Long and correspond to final case class
  def apply(auid: String, accessToken:String, idToken:String, scope:String, age: Long = DEF_AGE, id: UUID = UUID.random):Auth = {
    new Auth(auid, accessToken, idToken, scope, System.currentTimeMillis + age * 1000L,id)
  }
}

