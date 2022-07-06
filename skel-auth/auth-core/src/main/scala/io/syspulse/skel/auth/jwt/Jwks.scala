package io.syspulse.skel.auth.jwt

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


case class JWKSKey( 
      alg: String,
      kty: String, 
      use: String,
      n: String,
      e: String,
      kid: String)
case class JWKSKeys(keys:List[JWKSKey])

object JWKSKeys {
  implicit val jsonJWKSKey = jsonFormat6(JWKSKey)
  implicit val jsonJWKSKeys = jsonFormat1(JWKSKeys.apply)
}

trait Jwks {
  val log = Logger(s"${this.getClass().getName()}")

  //var jwks:Option[JWKSKeys] = None
  var jwks:Option[JWKSet] = None
  val matcher = (new JWKMatcher.Builder).publicOnly(true).keyType(KeyType.RSA).keyUse(KeyUse.SIGNATURE).build

  def setJwks(json:String):Option[JWKSet] = {
    jwks = Some(JWKSet.parse(json))  
    jwks
  }

  def verify(jwt:String,index:Int= -1):Boolean = {
    if(!jwks.isDefined) return false
    
    val selector = (new JWKSelector(matcher)).select(jwks.get)
    jwks.get.getKeys.asScala.zipWithIndex.collect{ case(key,i) if(index== -1 || i==index) => {
      log.info(s"Key[${i}]: ${key}: algo=${key.getAlgorithm()}")

      // create Public Key from JKS
      val publicKey = selector.get(i).asInstanceOf[RSAKey].toPublicKey
     
      val v = Jwt.decode(jwt,publicKey,JwtAlgorithm.allRSA())
      log.info(s"JWT Verification: pubkey=${publicKey}: v=${v}")
      v
    }}
    .filter(_.isSuccess)
    .size > 0
  }
}

