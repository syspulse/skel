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
import java.security.spec.RSAPublicKeySpec
import java.security.PublicKey
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateCrtKey
import java.math.BigInteger

class AuthJwt(uri:String = "") {
  val log = Logger(s"${this.getClass()}")

  // JWT ttl to UTC 
  implicit val clock = Clock.systemUTC //Clock.systemDefaultZone()

  protected var defaultSecret: String = ""
  // no default PublicKey
  protected var defaultPublicKey: Option[PublicKey] = None
  protected var defaultPrivateKey: Option[PrivateKey] = None

  
  protected var defaultAlgo:JwtAlgorithm = JwtAlgorithm.fromString(AuthJwt.DEFAULT_ALGO)
  protected var defaultAccessTokenTTL = AuthJwt.DEFAULT_ACCESS_TOKEN_TTL
  protected var defaultRefreshTokenTTL = AuthJwt.DEFAULT_REFRESH_TOKEN_TTL

  override def toString = s"${this.getClass().getSimpleName()}(${defaultAlgo},${Util.sha256(defaultSecret)},${defaultPublicKey},${defaultPrivateKey})"

  def getAlgo() = defaultAlgo.name
  def getSecret() = defaultSecret
  def getPublicKey() = defaultPublicKey

  def withSecret(secret:String) = {
    defaultSecret = secret
    this
  }

  def withPublicKey(publicKey:String) = {
    defaultPublicKey = Some(getPublicKey(publicKey))
    this
  }

  def withPublicKey(publicKey:PublicKey) = {
    defaultPublicKey = Some(publicKey)
    this
  }

  def withPrivateKey(privateKey:String) = {
    val (sk,pk) = AuthJwt.getPrivateKey(privateKey)
    defaultPrivateKey = Some(sk)
    defaultPublicKey = Some(pk)    
    this
  }

  def withAlgo(algo:String) = {
    defaultAlgo = JwtAlgorithm.fromString(algo)
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

  def getPublicKey(publicKey:String) = {
    val pk = try {
      val publicKeyPEM = publicKey
        .replace("-----BEGIN PUBLIC KEY-----", "")
        .replace("-----END PUBLIC KEY-----", "")
        .replaceAll("\\s+","")
        .replaceAll(System.lineSeparator(), "")    

      val encoded = Base64.getDecoder.decode(publicKeyPEM)

      val keyFactory:KeyFactory = KeyFactory.getInstance("RSA");
      val keySpec:X509EncodedKeySpec = new X509EncodedKeySpec(encoded)
      val pk:RSAPublicKey = keyFactory.generatePublic(keySpec).asInstanceOf[RSAPublicKey]
      
      pk

    } catch {
      case e:Exception => 
        log.error(s"failed to decode RSA PublicKey",e)
        throw e
    }
    pk
  }
  
  def withUri(uri:String) = {
    uri.trim.split("://|:").toList match {
      case algo :: Nil if(algo.toLowerCase == "hs256" | algo.toLowerCase == "hs512") =>
        this
          .withAlgo(algo.toUpperCase())
          .withSecret(AuthJwt.DEFAULT_SECRET)

      case algo :: secret :: Nil if(algo.toLowerCase == "hs256" | algo.toLowerCase == "hs512") =>
        this
          .withAlgo(algo.toUpperCase())
          .withSecret(secret)

      case algo :: "pk" :: ("cer" | "x509") :: file :: Nil if(algo.toLowerCase == "rs256" | algo.toLowerCase == "rs512") =>
        val pk = os.read(os.Path(file,os.pwd))
        this
          .withAlgo(algo.toUpperCase())
          .withPublicKey(pk)

      case ("http" | "https") :: _ =>
        val jwks = requests.get(uri).text()        
        val ppk = AuthJwt.getPublicKeyFromJWKS(jwks)
        // get the first public key
        log.info(s"PublicKeys: ${ppk}: applying: ${ppk(0)}")
        this
          .withAlgo(ppk(0)._1.toUpperCase())
          .withPublicKey(ppk(0)._2)

      case algo :: "pk" :: publicKey :: Nil if(algo.toLowerCase == "rs256" | algo.toLowerCase == "rs512") =>
        this
          .withAlgo(algo.toUpperCase())
          .withPublicKey(publicKey)

      case algo :: "sk" :: ("pkcs8"|"file") :: file :: Nil if(algo.toLowerCase == "rs256" | algo.toLowerCase == "rs512") =>
        val sk = os.read(os.Path(file,os.pwd))
        this
          .withAlgo(algo.toUpperCase())
          .withPrivateKey(sk)          

      case algo :: "sk" :: "hex" :: privateKey :: Nil if(algo.toLowerCase == "rs256" | algo.toLowerCase == "rs512") =>
        val sk = privateKey
        this
          .withAlgo(algo.toUpperCase())
          .withPrivateKey(sk)

      case algo :: "sk" :: privateKey :: Nil if(algo.toLowerCase == "rs256" | algo.toLowerCase == "rs512") =>
        val (sk,pk) = AuthJwt.getPrivateKey(privateKey)
        this
          .withAlgo(algo.toUpperCase())
          .withPrivateKey(privateKey)          

    }
    log.info(s"${this}")
    this
  }

  def decodeRawAll(token:String) = {
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
    algo:String = defaultAlgo.name,
    secret:String = defaultSecret,
    sub:Option[String] = None,
    aud:Option[String] = None):String = {
    
    generateToken(attr + ("id" -> id),expire,algo,secret,sub,aud)
  }

  def generateToken(
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:String = defaultAlgo.name,
    secret:String = defaultSecret,
    sub:Option[String] = None,
    aud:Option[String] = None):String = {

    val claim = attr.map{case (k,v) => s""""${k}":"${v}""""}.mkString(",")
    
    val c1 = JwtClaim(s"{${claim}}").issuedNow.expiresIn(expire)
    val c2 = if(sub.isDefined) c1.about(sub.getOrElse("")) else c1
    val c3 = if(aud.isDefined) c2.to(aud.getOrElse("")) else c2
    
    algo.toUpperCase().take(2) match {
      case "RS" => 
        val sk = defaultPrivateKey.get
        
        Jwt.encode(c3, sk, JwtAlgorithm.fromString(algo).asInstanceOf[JwtAsymmetricAlgorithm])

      case "HS" =>
        Jwt.encode(c3, secret, JwtAlgorithm.fromString(algo))
    }

  }

  def generateAccessToken(
    attr:Map[String,String] = Map(), 
    expire:Long = defaultAccessTokenTTL, 
    algo:String = defaultAlgo.name,
    secret:String = defaultSecret):String = generateToken(attr,expire,algo)   

  def generateRefreshToken(id:String) = Util.generateRandomToken()


  def decodeAll(token:String):Try[(Boolean,JwtHeader,JwtClaim,String)] = {
    Jwt.decodeAll(token,JwtOptions(signature = false)) match {
      case Success((header,claim,sig)) => 
        val algo = header.algorithm.getOrElse(defaultAlgo)
        val valid = algo.name.take(2) match {
          case "RS" =>
            try {
              
              val pk = defaultPublicKey.get
              
              //Jwt.decodeAll(token, rsa, Seq(algo.asInstanceOf[JwtAsymmetricAlgorithm]))
              Jwt.isValid(token, pk, Seq(algo.asInstanceOf[JwtAsymmetricAlgorithm]))
            } catch {
              case e:Exception => 
                log.error(s"failed to decode RSA JWT",e)
                false
            }
          case "HS" =>
            //Jwt.decodeAll(token, secret, Seq(algo.asInstanceOf[JwtHmacAlgorithm]))
            Jwt.isValid(token, defaultSecret, Seq(algo.asInstanceOf[JwtHmacAlgorithm]))
        }
        
        Success(
          (valid,header,claim,sig)
        )
      case f @ Failure(e) =>
        //log.error(s"could not decode JWT",e)
        Failure(e)
    }
  }

  // secret can be HMAC or PublicKey
  def isValid(token:String):Boolean = {
    val r = decodeAll(token)
    r.isSuccess && r.get._1
  }
    
  // this is called from HTTP Routes on every call !
  def verifyAuthToken(token: Option[String],id:String,data:Seq[Any]):Option[VerifiedToken] = token match {
    case Some(jwt) => {           
      val v = isValid(jwt)

      val uid = getClaim(jwt,"uid")
      val roles = getClaim(jwt,"roles").map(_.split(",").filter(!_.trim.isEmpty()).toSeq).getOrElse(Seq.empty)
      log.info(s"token=${jwt}: uid=${uid}: roles=${roles}: valid=${v}")

      if(v && !uid.isEmpty) 
        Some(VerifiedToken(uid.get,roles))
      else 
        None
    }
    case _ => None
  }

  if(!uri.isEmpty()) 
    withUri(uri)
}

case class VerifiedToken(uid:String,roles:Seq[String])

object AuthJwt {
  val log = Logger(s"${this.getClass()}")

  val DEFAULT_ACCESS_TOKEN_TTL = 3600L // in seconds
  val DEFAULT_REFRESH_TOKEN_TTL = 3600L * 24 * 5 // in seconds
  val DEFAULT_ACCESS_TOKEN_SERVICE_TTL = 3600L * 24 * 356 // in seconds
  val DEFAULT_ACCESS_TOKEN_ADMIN_TTL = 3600L * 24 * 30 // in seconds
  val DEFAULT_ALGO = "HS512"
  val DEFAULT_SECRET = Util.generateRandomToken(seed = Some("0xsecret"))

  // ATTENTION: default token to be compatible with all demos !
  var default = new AuthJwt().withSecret(DEFAULT_SECRET)

  def getPublicKeyFromJWKS(jwks:String):Seq[(String,PublicKey)] = {
    val json = ujson.read(jwks)
    val keys = json.obj("keys").arr

    val kf = KeyFactory.getInstance("RSA")

    val pp = keys.map( k => {
      val n = k.obj("n").str
      val e = k.obj("e").str
      val alg = k.obj("alg").str
      val x5c = k.obj.get("x5c").map(_.arr(0).str) // orE.arr(0).str

      val modulus = new BigInteger(1, Base64.getUrlDecoder().decode(n))
      val exponent = new BigInteger(1, Base64.getUrlDecoder().decode(e))
      val publicKey = kf.generatePublic(new RSAPublicKeySpec(modulus, exponent))

      (alg,publicKey)
    })    
    
    // val factory = CertificateFactory.getInstance("X.509")
    // val x5cData = Base64.getDecoder.decode(x5c)
    // val cert = factory
    //     .generateCertificate(new ByteArrayInputStream(x5cData))              
    // val publicKey = cert.getPublicKey().asInstanceOf[RSAPublicKey]
    log.debug(s"PK: ${pp}")
    pp.toSeq
  }

  def getPrivateKey(privateKey:String) = {
    val (sk,pk) = try {
      val encoded = {
        if(privateKey.startsWith("0x")) {
          Util.fromHexString(privateKey)
        } else {
          val privateKeyStr =  privateKey  
          .replace("-----BEGIN PRIVATE KEY-----", "")
          .replace("-----BEGIN RSA PRIVATE KEY-----", "")        
          .replace("-----END PRIVATE KEY-----", "")
          .replace("-----END RSA PRIVATE KEY-----", "")
          .replaceAll("\\s+","")
          .replaceAll(System.lineSeparator(), "")
      
          Base64.getDecoder.decode(privateKeyStr)
        }
      }

      val keyFactory:KeyFactory = KeyFactory.getInstance("RSA");
      val keySpec:PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(encoded);
      val sk:RSAPrivateKey = keyFactory.generatePrivate(keySpec).asInstanceOf[RSAPrivateKey]

      val e = sk.asInstanceOf[RSAPrivateCrtKey].getPublicExponent()
      
      val publicKeySpec:RSAPublicKeySpec = new RSAPublicKeySpec(sk.getModulus, e)     
      val pk:RSAPublicKey = keyFactory.generatePublic(publicKeySpec).asInstanceOf[RSAPublicKey]
      
      (sk,pk)

    } catch {
      case e:Exception => 
        log.error(s"failed to decode RSA PrivateKey",e)
        throw e
    }
    (sk,pk)
  }


  def apply() = default

  def apply(uri:String) = {
    default = new AuthJwt(uri)
    default
  }

  def verifyAuthToken(token: Option[String],id:String,data:Seq[Any]):Option[VerifiedToken] =
    default.verifyAuthToken(token,id,data)

  def getClaim(accessToken:String,claim:String):Option[String] =
    default.getClaim(accessToken,claim)

  def isValid(token:String):Boolean = 
    default.isValid(token)
}
