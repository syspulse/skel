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

import scala.util.{Try,Success,Failure}

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
import io.syspulse.skel.auth.Config
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.actor
import akka.http.scaladsl.marshalling.Marshal
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.StatusCode

final case class ProxyTokensAuthReq(username:String,password:String)
final case class ProxyTokensRes(id:Int,
                                firtsName:Option[String]=None,lastName:Option[String]=None,password:Option[String]=None,email:Option[String]=None,
                                userType:String="USER",verified:Boolean=false,
                                status:Option[String]=None,
                                token:String, refreshToken:String,
                                merchantId:Option[String]=None,storeUrl:Option[String]=None)

object ProxyM2MAuth extends DefaultJsonProtocol with NullOptions {
  implicit val tokensResFormat = jsonFormat(ProxyTokensRes,"id", "firts_name","last_name","password","email","type","verified","status","token","refresh_token","merchant_id","store_url")
  implicit val tokensAuthReqFormat = jsonFormat2(ProxyTokensAuthReq)
  
  def id = ProxyM2MAuth.getClass().getSimpleName()
}

case class ProxyTransformer(t:String,k:String,v:String)

class ProxyM2MAuth(val redirectUri:String,config:Config) extends Idp {
  
  import ProxyM2MAuth._

  val challenge = Util.generateAccessToken() //"challenge"
  val transformers:Seq[ProxyTransformer] = getTransfomers(config.proxyHeadersMapping)

  override def clientId:Option[String] = Some("10000") //Option[String](System.getenv("CUSTOM_AUTH_CLIENT_ID"))
  override def clientSecret:Option[String] = Some("$2a$10$9NERDMTtLDcgNPnixNqsv.Eol/4s81R5hPIPaP6LyKjhUPNVo821i") //Option[String](System.getenv("CUSTOM_AUTH_CLIENT_SECRET")) 

  def getGrantData():Map[String,String] =  Map("code_verifier" -> challenge)
  
  def getBasicAuth():Option[String] = None

  def getTokenUrl() = s"${config.proxyUri}/token" //"http://localhost:12090/auth/token"
  
  override def getRedirectUri() = s"${redirectUri}/custom"

  def getLoginUrl() = s"${config.proxyUri}/login"

  def getProfileUrl(accessToken:String):(String,Seq[(String,String)]) = ("",Seq())
      
  def withJWKS():ProxyM2MAuth = {
    log.warn("does not support JWKS store")
    this
  }

  def decodeTokens(tokenRsp:ByteString)(implicit mat:Materializer, ec: scala.concurrent.ExecutionContext):Future[IdpTokens] = {    
    Unmarshal(tokenRsp).to[ProxyTokensRes].map( t => IdpTokens(t.token,0,"","",""))
  }

  def decodeProfile(profileRsp:ByteString)(implicit mat:Materializer,ec: scala.concurrent.ExecutionContext):Future[OAuthProfile] = {
    Unmarshal(profileRsp).to[ProxyTokensRes].map( p => OAuthProfile(p.id.toString,email = "",name = "", "", ""))
  }  

  def getTransfomers(cfg:String):Seq[ProxyTransformer] = {
    if(cfg.isEmpty) return Seq()
    cfg.split(",").map(_.trim).map( s => {
      s.split(":") match {
        case Array(t,k,v) => ProxyTransformer(t.trim.toUpperCase,k.trim,v.trim)
        case Array(k,v) => ProxyTransformer("BODY",k.trim,v.trim)
      }
    })
  }

  def mapTransfomers(reqHeaders:Seq[HttpHeader],tt:Seq[ProxyTransformer]):Seq[ProxyTransformer] = {
    tt.flatMap(t => {
      reqHeaders.flatMap( h => {
        if(h.name == t.k) Some(t.copy(k = t.v ,v = h.value))
        else None
      })
    })
  }

  def transformBody(reqHeaders:Seq[HttpHeader]):String = transformBody(reqHeaders,transformers)

  def transformBody(reqHeaders:Seq[HttpHeader],trs:Seq[ProxyTransformer]):String = {
    val tt = mapTransfomers(reqHeaders,trs)
    println(s"tt=${tt}")

    var body = config.proxyBody
    for( t <- tt if t.t == "BODY") {
      body = body.replaceAll("""\{\{""" + t.k + """\}\}""", s"${t.v}")
    }
    body
  }

  def transformHeaders(reqHeaders:Seq[HttpHeader]):Seq[HttpHeader] = transformHeaders(reqHeaders,transformers)

  def transformHeaders(reqHeaders:Seq[HttpHeader],trs:Seq[ProxyTransformer]):Seq[HttpHeader] = {
    
    val tt = mapTransfomers(reqHeaders,trs)
    println(s"tt=${tt}")

    tt.filter(_.t.toUpperCase == "HEADER").flatMap( t => {
      t.v match {
        case "{{client_id}}" => Some(RawHeader(t.k,getClientId))
        case "{{client_secret}}" => Some(RawHeader(t.k,getClientSecret))
        case qv => {
          // try to get from property and then from envvar and then from value
          val v = qv.replaceAll("\\{\\{","").replaceAll("\\}\\}","")
          Seq(Option(System.getenv(v)),Option(System.getProperty(v)),Option(v)).flatten.headOption match {
            case Some(v) => Some(RawHeader(t.k,v))
            case _ => None
          }
        }
      }
    })
  }

  def askAuth(reqHeaders:Seq[HttpHeader],reqBody:String)(implicit config:Config,as:ActorSystem[_],ec: ExecutionContext):Future[Try[ProxyTokensRes]] = {
    val authBody = transformBody(reqHeaders) //authData.toJson.compactPrint

    val basicAuth = getBasicAuth()
    val headers = Seq[HttpHeader]() ++
      transformHeaders(reqHeaders) ++ 
      {if(basicAuth.isDefined) Seq(RawHeader("Authorization",s"Basic ${basicAuth.get}")) else Seq()}

    val idpTokenFuture = for {
      tokenReq <- {
        log.info(s"auth.uri=${getLoginUrl()}: requesting token:\nHeaders: ${headers}\nBody: ${authBody}")
        val rsp = Http().singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = getLoginUrl(),
            entity = HttpEntity(ContentTypes.`application/json`,authBody),
            headers = headers
        ))
        rsp
      }
      tokenRsp <- {
        log.info(s"auth.uri=${getLoginUrl()}: status=${tokenReq.status}")
        if(tokenReq.status == StatusCodes.OK)
          (tokenReq.entity.dataBytes.runFold(ByteString(""))(_ ++ _)).map(Success(_))
        else {
          log.error(s"auth.uri=${getLoginUrl()}: status=${tokenReq.status}: unauthorized")
          Future(Failure(new Exception(s"unauhorized: headers=${headers}: body=${reqBody}")))
        }
      }
      idpTokens <- {
        tokenRsp match {
          case Success(rsp) => {
            log.info(s"tokenRsp: ${rsp.utf8String}")
            Unmarshal(rsp).to[ProxyTokensRes].map(Success(_))
          }
          case f @ Failure(_) => Future(Failure(f.exception))
        }
      }
    } yield idpTokens

    idpTokenFuture
  }
}


