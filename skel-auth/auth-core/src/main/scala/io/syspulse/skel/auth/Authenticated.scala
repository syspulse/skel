package io.syspulse.skel.auth

import io.jvm.uuid.UUID

trait Authenticated {
  //def token: Option[OAuth2BearerToken] = None
  def getUser:Option[UUID] = None
  //def getRoles:List[String] = List.empty
}

case object AuthDisabled extends Authenticated