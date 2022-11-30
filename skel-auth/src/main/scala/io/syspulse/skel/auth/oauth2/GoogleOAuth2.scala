package io.syspulse.skel.auth.oauth2

import io.jvm.uuid._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ StatusCodes, HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger

import scala.util.Success
import scala.util.Try

import spray.json._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import spray.json.DefaultJsonProtocol._

import akka.http.scaladsl.client.RequestBuilding.{Post,Get}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.util.ByteString
import akka.http.scaladsl.model.HttpMethods
import java.nio.charset.StandardCharsets
import java.net.URLEncoder
import akka.http.scaladsl.model.HttpCharsets
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl.model.FormData

import requests._

import com.nimbusds.jose.jwk.JWKMatcher
import com.nimbusds.jose.jwk.KeyType
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.JWKSet

import scala.jdk.CollectionConverters._

import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.RSAKey
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim

import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.jwt.Jwks
import io.syspulse.skel.auth.Idp

import akka.stream.Materializer

final case class GoogleTokens(accessToken: String,expiresIn:Int, scope:String, tokenType:String, idToken:String)
final case class GoogleProfile(id:String,email:String,name:String,picture:String,locale:String)

object GoogleOAuth2 {
  implicit val googleTokensFormat = jsonFormat(GoogleTokens,"access_token","expires_in","scope", "token_type", "id_token")
  implicit val googleProfileFormat = jsonFormat(GoogleProfile,"id","email","name","picture","locale")

  def id = GoogleOAuth2.getClass().getSimpleName()
}

class GoogleOAuth2(val redirectUri:String) extends Idp {

  import GoogleOAuth2._
  override def clientId:Option[String] = Option[String](System.getenv("GOOGLE_AUTH_CLIENT_ID"))
  override def clientSecret:Option[String] = Option[String](System.getenv("GOOGLE_AUTH_CLIENT_SECRET")) 
  
  def getTokenUrl() = "https://oauth2.googleapis.com/token"

  override def getRedirectUri() = s"${redirectUri}/google"

  def getLoginUrl() =
    s"https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${getClientId}&scope=openid profile email&redirect_uri=${getRedirectUri()}"

  def getProfileUrl(accessToken:String):(String,Seq[(String,String)]) = 
    (s"https://www.googleapis.com/oauth2/v1/userinfo?alt=json&access_token=${accessToken}",Seq())

  def getBasicAuth():Option[String] = None

  def getGrantData():Map[String,String] = Map()

  def withJWKS():GoogleOAuth2 = {
    try {
      log.info("Requesting Google OpenID configuration...")
      val config = requests.get("https://accounts.google.com/.well-known/openid-configuration")
      log.info("Requesting Google OAuth2 JWKS...")
      val certsRsp = requests.get("https://www.googleapis.com/oauth2/v3/certs")
      
      //jwks = Some((certsRsp.text().parseJson).convertTo[JWKSKeys])
      jwks = setJwks(certsRsp.text())
      log.info(s"Google OAuth2 JWKS: ${jwks}")
    } catch {
      case e:Exception => log.error(s"failed to get JWKS",e)
    }

    this
  }

  def decodeTokens(tokenRsp:ByteString)(implicit mat:Materializer, ec: scala.concurrent.ExecutionContext):Future[IdpTokens] = {    
    Unmarshal(tokenRsp).to[GoogleTokens].map( t => IdpTokens(t.accessToken,t.expiresIn,t.scope,t.tokenType,t.idToken))
  }

  def decodeProfile(profileRsp:ByteString)(implicit mat:Materializer,ec: scala.concurrent.ExecutionContext):Future[OAuthProfile] = {
    Unmarshal(profileRsp).to[GoogleProfile].map( p => OAuthProfile(p.id,p.email,p.name,p.picture,p.locale))
  }
}


