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
import io.syspulse.skel.auth.oauth2.Idp

import akka.stream.Materializer
import io.syspulse.skel.crypto.Eth

final case class EthTokens(accessToken:String,idToken:String,expiresIn:Int,scope:String,tokenType:String,refreshToken:String)
final case class EthProfile(id:String,addr:String,email:String,avatar:String,createdAt:String)
final case class EthProfileData(data:EthProfile)

final case class EthTokenReq(code:String,client_id:String,client_secret:String,redirect_uri:String,grant_type:String)

object EthOAuth2 {
  implicit val jf_EthTokens = jsonFormat(EthTokens,"access_token", "id_token", "expires_in", "scope", "token_type", "refresh_token")
  implicit val jf_EthProfile = jsonFormat(EthProfile,"id","addr","email","avatar","created_at")
  implicit val jf_EthProfileData = jsonFormat(EthProfileData, "data")
  implicit val jf_EthTokenReq = jsonFormat(EthTokenReq, "code","client_id","client_secret","redirect_uri","grant_type")

  def id = EthOAuth2.getClass().getSimpleName()

  def generateSigDataTolerance(data:Map[String,String],tolerance:Long = (1000L * 60 * 60 * 24)):String = {
    val tsSig = System.currentTimeMillis() / tolerance
    val dataSig = data.map{ case(k,v) => s"${k}: ${v}"}
    // NOTE: BE VERY CAREFUL WITH BLANKS and COMMAS
    val sigData = s"timestamp: ${tsSig}" + (if(data.size>0) s",${dataSig.mkString(",")}" else "")
    sigData
  }

  def generateSigData(data:Map[String,String]):String = {
    val tsSig = System.currentTimeMillis()
    val dataSig = data.map{ case(k,v) => s"${k}: ${v}"}
    // NOTE: BE VERY CAREFUL WITH BLANKS and COMMAS
    val sigData = s"timestamp: ${tsSig}" + (if(data.size>0) s",${dataSig.mkString(",")}" else "")
    sigData
  }
}

class EthOAuth2(val uri:String) extends Idp {
  
  import EthOAuth2._

  override def clientId:Option[String] = Option[String](System.getenv("ETH_AUTH_CLIENT_ID"))
  override def clientSecret:Option[String] = Option[String](System.getenv("ETH_AUTH_CLIENT_SECRET")) 

  def getGrantData():Map[String,String] =  Map()
  
  def getBasicAuth():Option[String] = Some(java.util.Base64.getEncoder.encodeToString(s"${getClientId}:${getClientSecret}".getBytes()))

  def getTokenUrl() = s"${uri}/eth/token"

  val redirectUri:String = getRedirectUri()  
  override def getRedirectUri() = s"${uri}/eth/callback"

  // This is only for internal tests
  val addr = "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1" 
  def sig() = {
    val data = generateSigDataTolerance(Map("address" -> addr))
    val sigData = Util.hex(
      Eth.signMetamask(
        data,
        Eth.generate("0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db").get
      ).toArray()
    )
    //"0xd154fd4171820e35a1cf48e67242779714d176e59e19de02dcf62b78cd75946d0bd46da493810b66b589667286d05c0f4e1b0cc6f29a544361ad639b0a6614041c"
    (sigData,java.util.Base64.getEncoder.encodeToString(data.getBytes()))
  }
  
  def getLoginUrl() = {
    val (sigData,msg64) = sig()
    
    s"${uri}/eth/auth?msg=${msg64}&sig=${sigData}&addr=${addr}&response_type=code&client_id=${getClientId}&scope=profile&state=state&redirect_uri=${getRedirectUri()}"
  }

  def getProfileUrl(accessToken:String):(String,Seq[(String,String)]) = 
      (s"${uri}/eth/profile?access_token=${accessToken}",
       Seq(("Authorization",s"Bearer ${accessToken}")))

  def withJWKS():EthOAuth2 = {
    val jwksUri = s"${uri}/jwks"
    log.info(s"Requesting JWKS: ${jwksUri} ...")
    val certsRsp = requests.get(jwksUri)
    
    //jwks = Some((certsRsp.text().parseJson).convertTo[JWKSKeys])
    jwks = setJwks(certsRsp.text())
    log.info(s"Eth JWKS: ${jwks}")
    this
  }

  def decodeTokens(tokenRsp:ByteString)(implicit mat:Materializer, ec: scala.concurrent.ExecutionContext):Future[IdpTokens] = {    
    Unmarshal(tokenRsp).to[EthTokens].map( t => IdpTokens(t.accessToken,t.expiresIn,t.scope,t.tokenType,t.idToken,t.refreshToken))
  }

  def decodeProfile(profileRsp:ByteString)(implicit mat:Materializer,ec: scala.concurrent.ExecutionContext):Future[OAuthProfile] = {
    Unmarshal(profileRsp).to[EthProfile].map( p => OAuthProfile(p.id,p.email,p.addr,p.avatar,"universe"))
  }
    
}


