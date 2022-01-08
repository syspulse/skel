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

import io.syspulse.skel.auth.jwt.AuthJwt
import com.nimbusds.jose.jwk.JWKSelector
import com.nimbusds.jose.jwk.RSAKey
import pdi.jwt.Jwt
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim


class GoogleOAuth2 (
  redirectUri:String,
  clientId:Option[String] = Option[String](System.getenv("AUTH_CLIENT_ID")), 
  clientSecret:Option[String] = Option[String](System.getenv("AUTH_CLIENT_SECRET"))) extends Jwks {
  
  def getClientId = clientId.getOrElse("UNKNOWN")
  def getClientSecret = clientSecret.getOrElse("UNKNOW")
  def getTokenUrl = tokenUrl
  def getRedirectUri = redirectUri

  def loginUrl=
    s"https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${getClientId}&scope=openid profile email&redirect_uri=${redirectUri}"
  def tokenUrl = "https://oauth2.googleapis.com/token"

  def profileUrl(accessToken:String) = s"https://www.googleapis.com/oauth2/v1/userinfo?alt=json&access_token=${accessToken}"

  def withJWKS():GoogleOAuth2 = {
    log.info("Requesting Google OpenID configuration...")
    val config = requests.get("https://accounts.google.com/.well-known/openid-configuration")
    log.info("Requesting Google OAuth2 JWKS...")
    val certsRsp = requests.get("https://www.googleapis.com/oauth2/v3/certs")
    
    //jwks = Some((certsRsp.text().parseJson).convertTo[JWKSKeys])
    jwks = setJwks(certsRsp.text())
    log.info(s"Google OAuth2 JWKS: ${jwks}")

    this
  }

  override def toString = s"${this.getClass().getSimpleName()}(${loginUrl},${tokenUrl},${redirectUri})"
}

