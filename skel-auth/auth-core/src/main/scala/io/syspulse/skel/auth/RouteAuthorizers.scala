package io.syspulse.skel.auth

import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directive1

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.StatusCodes._

import akka.http.scaladsl.server.AuthenticationFailedRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection._
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.directives.Credentials

import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permissions.DefaultPermissions

case class AuthenticatedUser(uid:UUID,roles:Seq[String]) extends Authenticated {
  override def getUser: Option[UUID] = Some(uid)
  override def getRoles: Seq[String] = roles
}

trait RouteAuthorizers {
  val log = Logger(s"${this}")

  // case class VerifiedToken(uid:String,roles:Seq[String])
  
  // protected def verifyAuthToken(token: Option[String],id:String,data:Seq[Any]):Option[VerifiedToken] = token match {
  //   case Some(t) => {      
  //     val v = AuthJwt.isValid(t)
  //     val uid = AuthJwt.getClaim(t,"uid")
  //     val roles = AuthJwt.getClaim(t,"roles").getOrElse("").split(",").toSeq
  //     log.info(s"token=${token}: uid=${uid}: roles=${roles}: valid=${v}")
  //     if(v && !uid.isEmpty) 
  //       Some(VerifiedToken(uid.get,roles))
  //     else 
  //       None
  //   }
  //   case _ => None
  // }

  def authenticateAuthHeader(id:String,data:Seq[Any],header:String="X-Auth"): Directive[Tuple1[Option[String]]] =
    optionalHeaderValueByName(header)
      .tflatMap { case Tuple1(v) =>
        AuthJwt.verifyAuthToken(v,id,data) match {
          case Some(vt)    => provide(Some(vt.uid))
          case None         => reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2("X-Auth")))
        }
      }

  def oauth2Authenticator(credentials: Credentials): Option[AuthenticatedUser] = {
    log.info(s"credentials: ${credentials}")
    credentials match {
      case p @ Credentials.Provided(accessToken) => 
        AuthJwt.verifyAuthToken(Some(accessToken),"",Seq.empty)
          .map(vt => AuthenticatedUser(UUID(vt.uid),vt.roles))
      case _ => None
    }
  }

  // always authenticate regardless of credentials and return user
  def oauth2AuthenticatorGod(credentials: Credentials): Option[AuthenticatedUser] = {
    credentials match {
      case p @ Credentials.Provided(accessToken) => 
        AuthJwt.getClaim(accessToken,"uid").map(uid => AuthenticatedUser(UUID(uid),Seq("admin")))
      case _ => 
        Some(AuthenticatedUser(DefaultPermissions.USER_ADMIN,roles = Seq("admin")))
    }
  }

  // def loginRoute(addr: String) = get {
  //   rejectEmptyResponse {
  //     extractAuthToken(addr,Seq(addr)) { t => 
  //       UserAuth.updateUserAccessToken(addr) match {
  //         case Some((uid,accessToken)) => complete(LoginRes(accessToken,uid,addr))
  //         case _ => reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2("X-Sig")))
  //       }
  //     }
  //   }
  // }

  protected def authenticate(): Directive1[Authenticated] = {
    log.info(s"GOD=${Permissions.isGod}")
    if(Permissions.isGod) 
      // try to allo and inject correct user UID
      authenticateOAuth2("api",oauth2AuthenticatorGod)
    else
      authenticateOAuth2("api",oauth2Authenticator)
  }

  protected def authenticateAll[T](): Directive1[Authenticated] = {
    authenticate()
  }
}
