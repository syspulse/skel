package io.syspulse.skel.auth.proxy

sealed trait AuthResult {
  //def token: Option[OAuth2BearerToken] = None
}
case class BasicAuthResult(token: String) extends AuthResult
case class ProxyAuthResult(token: String) extends AuthResult
case object AuthDisabled extends AuthResult


