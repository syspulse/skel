package io.syspulse.skel.auth

import scala.collection.immutable
import io.jvm.uuid._

final case class Auth(auid: String, accessToken:String, idToken:String, scope:Option[String], expire: Long, id: UUID)
final case class Auths(auths: immutable.Seq[Auth])

final case class AuthRes(auth: Option[Auth])
final case class AuthCreateRes(auth: Auth)
final case class AuthActionRes(status: String,code:Option[String])

final case class AuthWithProfileRes(auid:String, idToken:String, email:String, name:String, avatar:String, locale:String)

object Auth {
  val DEF_AGE = 60 * 60 // seconds

  // accessToken cannot be here for prod. It is for RnD purposes here and will be removed
  // ATTENTION: age must be Long and correspond to final case class
  def apply(auid: String, accessToken:String, idToken:String, scope:Option[String], age: Long = DEF_AGE, id: UUID = UUID.random):Auth = {
    new Auth(auid, accessToken, idToken, scope.orElse(Some("")), System.currentTimeMillis + age * 1000L,id)
  }
}

