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
import io.syspulse.skel.auth.jwt.VerifiedToken
import pdi.jwt.Jwt
import pdi.jwt.JwtOptions
import pdi.jwt.JwtClaim

trait RouteAuthorizers {
  val log = Logger(s"${this}")
  
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
        AuthJwt
          .verifyAuthToken(Some(accessToken),"",Seq.empty)
          .map(vt => {
            AuthenticatedUser(UUID(vt.uid),vt.roles,Some(vt))
          })
      case _ => None
    }
  }

  // always authenticate regardless of credentials and return user
  def oauth2AuthenticatorGod(credentials: Credentials): Option[AuthenticatedUser] = {
    credentials match {
      case p @ Credentials.Provided(accessToken) => 
        val au = AuthJwt
          .getClaim(accessToken,"uid")
          .map(uid => 
            AuthenticatedUser(
              UUID(uid),
              Seq("admin"),
              Some(VerifiedToken(uid,Seq("admin"),claim = Jwt.decode(accessToken,JwtOptions(signature = false)).get))
          ))

        if(!au.isDefined) {
          log.warn(s"GOD=${Permissions.isGod}: JWT(uid) not found: using: ${DefaultPermissions.USER_1}")
          val au = AuthenticatedUser(DefaultPermissions.USER_1,roles = Seq("admin"),
                    Some(VerifiedToken(DefaultPermissions.USER_1.toString,Seq("admin"),claim = Jwt.decode(accessToken,JwtOptions(signature = false)).get)))
          Some(au)
        } else
          au
      case _ => 
        // attention: JWT claim is empty
        Some(AuthenticatedUser(DefaultPermissions.USER_ADMIN,roles = Seq("admin"),
            Some(VerifiedToken(DefaultPermissions.USER_ADMIN.toString,Seq("admin"),claim = JwtClaim()))))
    }
  }

  protected def authenticate(): Directive1[Authenticated] = {
    //log.debug(s"GOD=${Permissions.isGod}")
    val a:Directive1[Authenticated] = if(Permissions.isGod) 
      // try to allo and inject correct user UID
      authenticateOAuth2("api",oauth2AuthenticatorGod)
    else
      authenticateOAuth2("api",oauth2Authenticator)
    log.debug(s"GOD=${Permissions.isGod}: authenticated=${a}")
    a
  }

  protected def authenticateAll[T](): Directive1[Authenticated] = {
    authenticate()
  }
}
