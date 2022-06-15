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

import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.jwt.Jwks
import io.syspulse.skel.auth.Idp

import akka.stream.Materializer

final case class EthTokens(accessToken:String,expiresIn:Int,scope:String,tokenType:String)
final case class EthProfile(id:String,addr:String,email:String,avatar:String,createdAt:String)
final case class EthProfileData(data:EthProfile)

final case class EthTokenReq(code:String,client_id:String,client_secret:String,redirect_uri:String,grant_type:String)

object EthOAuth2 {
  implicit val jf_EthTokens = jsonFormat(EthTokens,"access_token", "expires_in", "scope", "token_type")
  implicit val jf_EthProfile = jsonFormat(EthProfile,"id","addr","email","avatar","created_at")
  implicit val jf_EthProfileData = jsonFormat(EthProfileData, "data")
  implicit val jf_EthTokenReq = jsonFormat(EthTokenReq, "code","client_id","client_secret","redirect_uri","grant_type")

  def id = EthOAuth2.getClass().getSimpleName()
}

class EthOAuth2(val uri:String) extends Idp {
  
  import EthOAuth2._


  override def clientId:Option[String] = Option[String](System.getenv("ETH_AUTH_CLIENT_ID"))
  override def clientSecret:Option[String] = Option[String](System.getenv("ETH_AUTH_CLIENT_SECRET")) 

  def getGrantHeaders():Map[String,String] =  Map()
  
  def getBasicAuth():Option[String] = Some(java.util.Base64.getEncoder.encodeToString(s"${getClientId}:${getClientSecret}".getBytes()))

  def getTokenUrl() = s"${uri}/eth/token"

  val redirectUri:String = getRedirectUri()  
  override def getRedirectUri() = s"${uri}/eth/callback"

  val sig = "0xd154fd4171820e35a1cf48e67242779714d176e59e19de02dcf62b78cd75946d0bd46da493810b66b589667286d05c0f4e1b0cc6f29a544361ad639b0a6614041c"
  def getLoginUrl() =
    s"${uri}/eth/auth?sig=${sig}&response_type=code&client_id=${getClientId}&scope=profile&state=state&redirect_uri=${getRedirectUri()}"

  def getProfileUrl(accessToken:String):(String,Seq[(String,String)]) = 
      (s"${uri}/eth/profile?access_token=${accessToken}",
       Seq(("Authorization",s"Bearer ${accessToken}")))

  def withJWKS():EthOAuth2 = {
    log.warn("does not support JWKS store")
    this
  }

  def decodeTokens(tokenRsp:ByteString)(implicit mat:Materializer, ec: scala.concurrent.ExecutionContext):Future[IdpTokens] = {    
    Unmarshal(tokenRsp).to[EthTokens].map( t => IdpTokens(t.accessToken,t.expiresIn,t.scope,t.tokenType,""))
  }

  def decodeProfile(profileRsp:ByteString)(implicit mat:Materializer,ec: scala.concurrent.ExecutionContext):Future[OAuthProfile] = {
    Unmarshal(profileRsp).to[EthProfile].map( p => OAuthProfile(p.id,p.email,p.addr,p.avatar,"Sith"))
  }
    
}


