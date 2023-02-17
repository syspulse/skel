package io.syspulse.skel.auth.jwt

import scala.util.Success
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import java.time.Clock

import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import pdi.jwt.algorithms.JwtHmacAlgorithm
import pdi.jwt.algorithms.JwtAsymmetricAlgorithm

import com.nimbusds.jose.jwk._
import ujson._

import io.syspulse.skel.auth.Auth
import io.syspulse.skel.util.Util
import scala.util.Try

object AuthJwt {
  val log = Logger(s"${this}")

  // JWT ttl to UTC 
  implicit val clock = Clock.systemUTC //Clock.systemDefaultZone()

  val DEFAULT_ACCESS_TOKEN_TTL = 3600L // in seconds
  val DEFAULT_REFRESH_TOKEN_TTL = 3600L * 24 * 5 // in seconds
  val DEFAULT_ACCESS_TOKEN_SERVICE_TTL = 3600L * 24 * 356 // in seconds
  val DEFAULT_ACCESS_TOKEN_ADMIN_TTL = 3600L * 24 * 30 // in seconds

  // ATTENTION: it is non-secure deterministic on purpose !
  var defaultSecret: String = Util.generateRandomToken(seed = Some("0xsecret"))

  var defaultAlgo:JwtAlgorithm = JwtAlgorithm.HS512
  var defaultAccessTokenTTL = DEFAULT_ACCESS_TOKEN_TTL
  var defaultRefreshTokenTTL = DEFAULT_REFRESH_TOKEN_TTL

  def withSecret(secret:String) = {
    defaultSecret = secret
    this
  }

  def withAlgo(algo:JwtAlgorithm) = {
    defaultAlgo = algo
    this
  }

  def withAccessTokenTTL(ttl:Long) = {
    defaultAccessTokenTTL = ttl
    this
  }

  def withRefreshTokenTTL(ttl:Long) = {
    defaultRefreshTokenTTL = ttl
    this
  }

  def decodeAll(token:String) = {
    Jwt.decodeRawAll(token,JwtOptions(signature = false))
  }

  def decodeIdToken(t:String):Try[JwtClaim] = {
    Jwt.decode(t,JwtOptions(signature = false))
  }
  def decodeAccessToken(t:String):Try[JwtClaim] = {
    Jwt.decode(t,JwtOptions(signature = false))
  }
  def decodeIdClaim(t:String):Map[String,String] = {
    val jwt = decodeIdToken(t)
    if(jwt.isSuccess)
      ujson.read(jwt.get.toJson).obj.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap
    else 
      Map()
  }

  def decodeIdToken(a:Auth):Try[JwtClaim] = decodeIdToken(a.idToken.getOrElse(""))
  def decodeAccessToken(a:Auth):Try[JwtClaim] = decodeAccessToken(a.accessToken)
  def decodeIdClaim(a:Auth):Map[String,String] = decodeIdClaim(a.idToken.getOrElse(""))

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

  def generateIdToken(id:String, 
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:JwtAlgorithm = defaultAlgo,
    secret:String = defaultSecret):String = {
    
    val claim = (attr + ("id" -> id)).map{case (k,v) => s""""${k}":"${v}""""}.mkString(",")
    Jwt
      .encode(JwtClaim(s"{${claim}}")
      //.encode(JwtClaim({s"""{"id":"${id}"}"""})
      .issuedNow.expiresIn(expire), secret, algo)
  }

  def generateToken(
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:JwtAlgorithm = defaultAlgo,
    secret:String = defaultSecret):String = {

    val claim = attr.map{case (k,v) => s""""${k}":"${v}""""}.mkString(",")
    Jwt
      .encode(JwtClaim(s"{${claim}}")
      .issuedNow.expiresIn(expire), secret, algo)
  }

  def generateAccessToken(
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:JwtAlgorithm = defaultAlgo,
    secret:String = defaultSecret):String = generateToken(attr,expire,algo)   

  def generateRefreshToken(id:String) = Util.generateRandomToken()

  def isValid(token:String, algo:JwtAlgorithm = defaultAlgo,secret:String = defaultSecret) = {
    algo match {
      case a:JwtHmacAlgorithm => Jwt.isValid(token, secret, Seq(a))
      case a:JwtAsymmetricAlgorithm => Jwt.isValid(token, secret, Seq(a))
      case _  => 
        log.error(s"unknwon algo: ${algo.getClass().getName()}")
        false
    }
    
  }
}

