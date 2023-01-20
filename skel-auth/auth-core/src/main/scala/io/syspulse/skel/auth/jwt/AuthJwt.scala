package io.syspulse.skel.auth.jwt

import scala.util.Success
import com.typesafe.scalalogging.Logger

import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import com.nimbusds.jose.jwk._

import io.jvm.uuid._
import ujson._

import io.syspulse.skel.auth.Auth
import java.time.Clock
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm

import ujson._


object AuthJwt {
  val log = Logger(s"${this}")
  implicit val clock = Clock.systemDefaultZone()

  var secret: String = "secret"

  def run(jwtSecret:String) = {
    secret = jwtSecret
  }

  def decodeIdToken(a:Auth) = {
    Jwt.decode(a.idToken,JwtOptions(signature = false))
  }

  def decodeAccessToken(a:Auth) = {
    Jwt.decode(a.accessToken,JwtOptions(signature = false))
  }

  def decodeAll(token:String) = {
    Jwt.decodeRawAll(token,JwtOptions(signature = false))
  }

  def decodeIdClaim(a:Auth):Map[String,String] = {
    val jwt = decodeIdToken(a)
    if(jwt.isSuccess)
      ujson.read(jwt.get.toJson).obj.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap
    else 
      Map()
  }

  def getClaim(accessToken:String,claim:String):Option[String] = {
    val data = Jwt.decode(accessToken,JwtOptions(signature = false))
    data match {
      case Success(c) => {
        try {
          val json = ujson.read(c.content)
          Some(json.obj(claim).str)
        } catch {
          case e:Exception => log.error(s"failed to parse claim: ${c}");
            None
        }
      }
      case _ => None
    }
  }

  def generateIdToken(id:String, attr:Map[String,String] = Map(), expire:Long = 3600L, algo:JwtAlgorithm=JwtAlgorithm.HS512):String = {
    val claim = (attr + ("id" -> id)).map{case (k,v) => s""""${k}":"${v}""""}.mkString(",")
    Jwt
      .encode(JwtClaim(s"{${claim}}")
      //.encode(JwtClaim({s"""{"id":"${id}"}"""})
      .issuedNow.expiresIn(expire), secret, algo)
  }

  def generateToken(attr:Map[String,String] = Map(), expire:Long = 3600L, algo:JwtAlgorithm=JwtAlgorithm.HS512):String = {
    val claim = attr.map{case (k,v) => s""""${k}":"${v}""""}.mkString(",")
    Jwt
      .encode(JwtClaim(s"{${claim}}")
      .issuedNow.expiresIn(expire), secret, algo)
  }

  def generateAccessToken(attr:Map[String,String] = Map(), expire:Long = 3600L, algo:JwtAlgorithm=JwtAlgorithm.HS512):String = 
    generateToken(attr,expire,algo)   

  def isValid(token:String, algo:JwtAlgorithm=JwtAlgorithm.HS512) = {
    algo match {
      case a:JwtHmacAlgorithm => Jwt.isValid(token, secret, Seq(a))
      case a:JwtAsymmetricAlgorithm => Jwt.isValid(token, secret, Seq(a))
      case _  => false
    }
    
  }
}

