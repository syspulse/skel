package io.syspulse.skel.auth

import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.AuthenticationFailedRejection
import akka.http.scaladsl.server.AuthenticationFailedRejection._
import akka.http.scaladsl.model.headers.HttpChallenges
import akka.http.scaladsl.server.directives.Credentials
import io.syspulse.skel.auth.jwt.AuthJwt

case class AuthenticatedUser(uid:UUID) extends Authenticated {
  override def getUser: Option[UUID] = Some(uid)
}

trait RouteAuthorizers {
  val log = Logger(s"${this}")
  
  protected def verifyAuthToken(token: Option[String],id:String,data:Seq[Any]):Option[String] = token match {
    case Some(t) => {
      val v = AuthJwt.isValid(t)
      val uid = AuthJwt.getClaim(t,"uid")
      log.info(s"token=${token}: uid=${uid}: valid=${v}")
      if(v && !uid.isEmpty) uid else None
    }
    case _ => None
  }

  def authenticateAuthHeader(id:String,data:Seq[Any],header:String="X-Auth"): Directive[Tuple1[Option[String]]] =
    optionalHeaderValueByName(header)
      .tflatMap { case Tuple1(v) =>
        verifyAuthToken(v,id,data) match {
          case Some(uid)    => provide(v) 
          case None         => reject(AuthenticationFailedRejection(CredentialsRejected, HttpChallenges.oAuth2("X-Auth")))
        }
      }

  def oauth2Authenticator(credentials: Credentials): Option[AuthenticatedUser] = {
    log.info(s"credentials=${credentials}")
    credentials match {
      case p @ Credentials.Provided(accessToken) => verifyAuthToken(Some(accessToken),"",Seq.empty).map(uid => AuthenticatedUser(UUID(uid)))
      case _ => None
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

    
}
