package io.syspulse.skel.auth.oauth2

import akka.util.ByteString
import scala.concurrent.Future
import akka.stream.Materializer

final case class IdpTokens(accessToken: String,expiresIn:Int, scope:String, tokenType:String, idToken:String)
final case class OAuthProfile(id:String,email:String,name:String,picture:String,locale:String)

trait OAuth2 {
  def clientId:Option[String] = Option[String](System.getenv("AUTH_CLIENT_ID")) 
  def clientSecret:Option[String] = Option[String](System.getenv("AUTH_CLIENT_SECRET")) 
  
  def redirectUri:String

  def getClientId = clientId.getOrElse("UNKNOWN")
  def getClientSecret = clientSecret.getOrElse("UNKNOW")
  def getLoginUrl():String
  def getTokenUrl():String
  def getRedirectUri():String = redirectUri
  def getProfileUrl(accessToken:String):(String,Seq[(String,String)])
  def getGrantData():Map[String,String]

  def decodeTokens(tokenRsp:ByteString)(implicit mat:Materializer,ec: scala.concurrent.ExecutionContext):Future[IdpTokens]
  def decodeProfile(profileRsp:ByteString)(implicit mat:Materializer,ec: scala.concurrent.ExecutionContext):Future[OAuthProfile]

  def withJWKS():OAuth2

  def getBasicAuth():Option[String]

  override def toString = s"${this.getClass().getSimpleName()}(${getLoginUrl()},${getTokenUrl()},${getRedirectUri()})"
}

