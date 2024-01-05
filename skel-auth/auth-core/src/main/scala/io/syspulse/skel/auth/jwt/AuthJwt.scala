package io.syspulse.skel.auth.jwt

import scala.util.{Success,Failure,Try}
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
import java.util.Base64
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.security.interfaces.RSAPrivateKey
import pdi.jwt.algorithms.JwtRSAAlgorithm
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.RSAPublicKey

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

  val DEFAULT_ALGO = "HS512"
  var defaultAlgo:JwtAlgorithm = JwtAlgorithm.fromString(DEFAULT_ALGO)
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
          case e:Exception => log.warn(s"failed to parse claim: '${claim}'");
            None
        }
      }
      case _ => None
    }
  }

  def generateIdToken(
    id:String, 
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:String = DEFAULT_ALGO,
    secret:String = defaultSecret,
    sub:Option[String] = None,
    aud:Option[String] = None):String = {
    
    generateToken(attr + ("id" -> id),expire,algo,secret,sub,aud)
  }

  def generateToken(
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:String = DEFAULT_ALGO,
    secret:String = defaultSecret,
    sub:Option[String] = None,
    aud:Option[String] = None):String = {

    val claim = attr.map{case (k,v) => s""""${k}":"${v}""""}.mkString(",")
    
    val c1 = JwtClaim(s"{${claim}}").issuedNow.expiresIn(expire)
    val c2 = if(sub.isDefined) c1.about(sub.getOrElse("")) else c1
    val c3 = if(aud.isDefined) c2.to(aud.getOrElse("")) else c2

    algo.toUpperCase().take(2) match {
      case "RS" => 
        val privateKeyPEM = secret
          .replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----BEGIN RSA PRIVATE KEY-----", "")
          .replaceAll(System.lineSeparator(), "")
          .replace("-----END PRIVATE KEY-----", "")
          .replace("-----END RSA PRIVATE KEY-----", "")

        val encoded = Base64.getDecoder.decode(privateKeyPEM)

        val keyFactory:KeyFactory = KeyFactory.getInstance("RSA");
        val keySpec:PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(encoded);
        val rsa:RSAPrivateKey = keyFactory.generatePrivate(keySpec).asInstanceOf[RSAPrivateKey] 
        
        Jwt.encode(c3, rsa, JwtAlgorithm.fromString(algo).asInstanceOf[JwtAsymmetricAlgorithm])

      case "HS" =>
        //
        Jwt.encode(c3, secret, JwtAlgorithm.fromString(algo))
    }

  }

  def generateAccessToken(
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:String = DEFAULT_ALGO,
    secret:String = defaultSecret):String = generateToken(attr,expire,algo)   

  def generateRefreshToken(id:String) = Util.generateRandomToken()

  // def isValid(token:String, algo:JwtAlgorithm,secret:String):Boolean = {
  //   algo match {
  //     case a:JwtHmacAlgorithm => Jwt.isValid(token, secret, Seq(a))
  //     case a:JwtAsymmetricAlgorithm => Jwt.isValid(token, secret, Seq(a))
  //     case _  => 
  //       log.error(s"unknwon algo: ${algo.getClass().getName()}")
  //       false
  //   }    
  // }

  def decodeAll(token:String,algo:String,secret:String):Try[(Boolean,JwtHeader,JwtClaim,String)] = {
    Jwt.decodeAll(token,JwtOptions(signature = false)) match {
      case Success((header,claim,sig)) => 
        val algo = header.algorithm.getOrElse(defaultAlgo)
        val valid = algo.name.take(2) match {
          case "RS" =>
            try {
              val privateKeyPEM = secret
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replaceAll(System.lineSeparator(), "")
                .replace("-----END PUBLIC KEY-----", "")              

              val encoded = Base64.getDecoder.decode(privateKeyPEM)

              val keyFactory:KeyFactory = KeyFactory.getInstance("RSA");
              val keySpec:X509EncodedKeySpec = new X509EncodedKeySpec(encoded)
              val rsa:RSAPublicKey = keyFactory.generatePublic(keySpec).asInstanceOf[RSAPublicKey]
              
              //Jwt.decodeAll(token, rsa, Seq(algo.asInstanceOf[JwtAsymmetricAlgorithm]))
              Jwt.isValid(token, rsa, Seq(algo.asInstanceOf[JwtAsymmetricAlgorithm]))
            } catch {
              case e:Exception => 
                log.error(s"failed to decode RSA JWT",e)
                false
            }
          case "HS" =>
            //Jwt.decodeAll(token, secret, Seq(algo.asInstanceOf[JwtHmacAlgorithm]))
            Jwt.isValid(token, secret, Seq(algo.asInstanceOf[JwtHmacAlgorithm]))
        }
        
        Success(
          (valid,header,claim,sig)
        )
      case f @ Failure(e) =>
        log.error(s"could not decode JWT",e)
        Failure(e)
    }
  }

  // secret can be HMAC or PublicKey
  def isValid(token:String):Boolean = {
    // Jwt.decodeAll(token,JwtOptions(signature = false)) match {
    //   case Success((header,claim,sig)) => 
    //     val algo = header.algorithm.getOrElse(defaultAlgo)
    //     isValid(token, algo ,secret)
    //   case Failure(e) =>
    //     log.error(s"could not decode JWT",e)
    //     false
    // } 
    val r = decodeAll(token,DEFAULT_ALGO,defaultSecret)
    r.isSuccess && r.get._1
  }

  case class VerifiedToken(uid:String,roles:Seq[String])
  
  def verifyAuthToken(token: Option[String],id:String,data:Seq[Any]):Option[VerifiedToken] = token match {
    case Some(jwt) => {           
      val v = AuthJwt.isValid(jwt)

      val uid = AuthJwt.getClaim(jwt,"uid")
      val roles = AuthJwt.getClaim(jwt,"roles").map(_.split(",").filter(!_.trim.isEmpty()).toSeq).getOrElse(Seq.empty)
      log.info(s"token=${jwt}: uid=${uid}: roles=${roles}: valid=${v}")
      if(v && !uid.isEmpty) 
        Some(VerifiedToken(uid.get,roles))
      else 
        None
    }
    case _ => None
  }
}

