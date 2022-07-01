package io.syspulse.skel.auth.jwt

import com.typesafe.scalalogging.Logger

import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import com.nimbusds.jose.jwk._

import io.jvm.uuid._
import ujson._

import io.syspulse.skel.auth.Auth
import java.time.Clock
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm
import io.syspulse.skel.auth.Config

object AuthJwt {
  val log = Logger(s"${this}")
  implicit val clock = Clock.systemDefaultZone()

  var secret: String = "secret"

  def run(config:Config) = {
    secret = config.jwtSecret
  }

  def decode(a:Auth) = {
    Jwt.decode(a.idToken,JwtOptions(signature = false))
  }

  def decodeClaim(a:Auth):Map[String,String] = {
    val jwt = decode(a)
    println(s"${jwt} ===============> ${jwt.get.toJson}")
    if(jwt.isSuccess)
      ujson.read(jwt.get.toJson).obj.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap
    else 
      Map()
  }

  def generateIdToken(uid:String, expire:Long = 3600L, algo:JwtAlgorithm=JwtAlgorithm.HS512):String = {
    Jwt
      .encode(JwtClaim({s"""{"uid":"${uid}"}"""})
      .issuedNow.expiresIn(expire), secret, algo)
  }

  def generateAccessToken(attr:Map[String,String] = Map(), expire:Long = 3600L, algo:JwtAlgorithm=JwtAlgorithm.HS512):String = {
    val claim = attr.map{case (k,v) => s""""${k}":"${v}""""}.mkString(",")
    Jwt
      .encode(JwtClaim(s"{${claim}}")
      .issuedNow.expiresIn(expire), secret, algo)
  }

  def isValid(token:String, algo:JwtAlgorithm=JwtAlgorithm.HS512) = {
    algo match {
      case a:JwtHmacAlgorithm => Jwt.isValid(token, secret, Seq(a))
      case a:JwtAsymmetricAlgorithm => Jwt.isValid(token, secret, Seq(a))
      case _  => false
    }
    
  }
}

