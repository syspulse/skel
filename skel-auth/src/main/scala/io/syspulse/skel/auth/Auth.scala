package io.syspulse.skel.auth

import scala.collection.immutable
import io.jvm.uuid._

//final case class Auth(accessToken:String, idToken:String, uid: Option[UUID], scope:Option[String], expire: Long)
final case class Auths(auths: immutable.Seq[Auth])

final case class AuthRes(auth: Option[Auth])
final case class AuthCreateRes(auth: Auth)
final case class AuthActionRes(status: String,code:Option[String])

final case class AuthIdp(accessToken:String, idToken:String, refreshToken:String)
final case class AuthWithProfileRes(accessToken:String, idp: AuthIdp, uid: Option[UUID], xid:String, email:String, name:String, avatar:String, locale:String)

