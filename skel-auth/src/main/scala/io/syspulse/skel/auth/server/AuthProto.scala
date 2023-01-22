package io.syspulse.skel.auth.server

import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.auth.Auth

final case class Auths(auths: immutable.Seq[Auth])

final case class AuthRes(auth: Option[Auth])
final case class AuthCreateRes(auth: Auth)
final case class AuthActionRes(status: String,code:Option[String])

final case class AuthIdp(accessToken:String, idToken:String, refreshToken:String, provider:Option[String] = None)

final case class AuthWithProfileRes(
  accessToken:String, 
  idToken:Option[String] = None,
  refreshToken:Option[String] = None,
  idp: AuthIdp, 
  uid: Option[UUID], xid:String, email:String, name:String, avatar:String, locale:String
)

