package io.syspulse.skel.auth

import io.jvm.uuid.UUID
import io.syspulse.skel.auth.jwt.VerifiedToken

trait Authenticated {
  //def token: Option[OAuth2BearerToken] = None
  def getUser:Option[UUID] = None
  def getRoles:Seq[String] = Seq.empty
  def getToken:Option[VerifiedToken] = None
}

case object AuthDisabled extends Authenticated

case class AuthenticatedUser(uid:UUID,roles:Seq[String],token:Option[VerifiedToken] = None) extends Authenticated {
  override def getUser: Option[UUID] = Some(uid)
  override def getRoles: Seq[String] = roles
  override def getToken:Option[VerifiedToken] = token
}