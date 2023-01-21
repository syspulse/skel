package io.syspulse.skel.auth

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.auth.jwt.AuthJwt

final case class Auth(accessToken:String, idToken:String, refreshToken:String, uid: Option[UUID], scope:Option[String], expire: Long, ts:Option[Long] = None)

object Auth {
  val DEF_AGE:Long = AuthJwt.DEFAULT_ACCESS_TOKEN_TTL
  val NOBODY_AGE:Long = 60L * 30 // 30 minutes to complete enrollment

  // accessToken cannot be here for prod. It is for RnD purposes here and will be removed
  // ATTENTION: age must be Long and correspond to final case class
  def apply(accessToken:String, idToken:String, refreshToken:String, uid: Option[UUID], scope:Option[String], age: Long = DEF_AGE, ts:Option[Long] = Some(System.currentTimeMillis())):Auth = {
    new Auth(
      accessToken, 
      idToken, 
      refreshToken, 
      uid, 
      scope.orElse(Some("")), 
      expire = ts.get + age * 1000L,
      ts = ts
    )
  }
}

