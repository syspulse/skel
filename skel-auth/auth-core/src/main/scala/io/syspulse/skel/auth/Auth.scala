package io.syspulse.skel.auth

import scala.collection.immutable
import io.jvm.uuid._

final case class Auth(accessToken:String, idToken:String, refreshToken:String, uid: Option[UUID], scope:Option[String], expire: Long)

object Auth {
  val DEF_AGE = 60 * 60 // seconds

  // accessToken cannot be here for prod. It is for RnD purposes here and will be removed
  // ATTENTION: age must be Long and correspond to final case class
  def apply(accessToken:String, idToken:String, refreshToken:String, uid: Option[UUID], scope:Option[String], age: Long = DEF_AGE):Auth = {
    new Auth(accessToken, idToken, refreshToken, uid, scope.orElse(Some("")), System.currentTimeMillis + age * 1000L)
  }
}

