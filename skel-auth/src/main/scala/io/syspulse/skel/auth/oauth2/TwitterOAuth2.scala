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


final case class TwitterTokens(accessToken:String,expiresIn:Int,scope:String,tokenType:String)
final case class TwitterProfile(id:String,name:String,profileImageUrl:String,createdAt:String)
final case class TwitterProfileData(data:TwitterProfile)

object TwitterOAuth2 {
  implicit val twitterTokensFormat = jsonFormat(TwitterTokens,"access_token", "expires_in", "scope", "token_type")
  implicit val twitterProfileFormat = jsonFormat(TwitterProfile,"id","name","profile_image_url","created_at")
  implicit val twitterProfileDataFormat = jsonFormat(TwitterProfileData, "data")

  def id = TwitterOAuth2.getClass().getSimpleName()
}

class TwitterOAuth2(val redirectUri:String) extends Idp {
  
  import TwitterOAuth2._

  // https://www.oauth.com/oauth2-servers/pkce/authorization-request/
  // THis is only for internal flow, FE Clients generate their own challeges
  val challenge = s"challenge-${(System.currentTimeMillis / (1000 * 60 * 60)).toString}" // "challenge"

  override def clientId:Option[String] = Option[String](System.getenv("TWITTER_AUTH_CLIENT_ID"))
  override def clientSecret:Option[String] = Option[String](System.getenv("TWITTER_AUTH_CLIENT_SECRET")) 

  def getGrantData():Map[String,String] =  Map("code_verifier" -> challenge)
  
  def getBasicAuth():Option[String] = Some(java.util.Base64.getEncoder.encodeToString(s"${getClientId}:${getClientSecret}".getBytes()))

  def getTokenUrl() = "https://api.twitter.com/2/oauth2/token"
  
  override def getRedirectUri() = s"${redirectUri}/twitter"

  def getLoginUrl() =
    s"https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${getClientId}&redirect_uri=${getRedirectUri()}&scope=users.read tweet.read&state=state&code_challenge=${challenge}&code_challenge_method=plain"

  def getProfileUrl(accessToken:String):(String,Seq[(String,String)]) = 
      (s"https://api.twitter.com/2/users/me?user.fields=id,name,profile_image_url,location,created_at,url,description,entities,public_metrics",
       Seq(("Authorization",s"Bearer ${accessToken}")))

  def withJWKS():TwitterOAuth2 = {
    log.warn("does not support JWKS store")
    this
  }

  def decodeTokens(tokenRsp:ByteString)(implicit mat:Materializer, ec: scala.concurrent.ExecutionContext):Future[IdpTokens] = {    
    Unmarshal(tokenRsp).to[TwitterTokens].map( t => IdpTokens(t.accessToken,t.expiresIn,t.scope,t.tokenType,""))
  }

  def decodeProfile(profileRsp:ByteString)(implicit mat:Materializer,ec: scala.concurrent.ExecutionContext):Future[OAuthProfile] = {
    Unmarshal(profileRsp).to[TwitterProfileData].map( p => OAuthProfile(p.data.id,p.data.name,"",p.data.profileImageUrl,""))
  }
    
}


