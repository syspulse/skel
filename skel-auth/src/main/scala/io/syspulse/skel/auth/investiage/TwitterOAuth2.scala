package io.syspulse.skel.auth.investigate

import requests._
import scalatags.Text.all._
import scalatags.Text.tags2
import java.time.Clock
import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import com.nimbusds.jose.jwk._

object TwitterOAuth2 extends cask.MainRoutes{
  override def port = 3001

  val clientId = Option(System.getenv("AUTH_CLIENT_ID")).getOrElse("UNKNOWN")
  val clientSecret = Option(System.getenv("AUTH_CLIENT_SECRET")).getOrElse("UNKNOWN")
  val basicAuth = java.util.Base64.getEncoder.encodeToString(s"${clientId}:${clientSecret}".getBytes())
  val redirectUri = "http://localhost:3001/callback"
  
  val loginUrl=
    s"https://twitter.com/i/oauth2/authorize?response_type=code&client_id=${clientId}&redirect_uri=${redirectUri}&scope=users.read tweet.read&state=state&code_challenge=challenge&code_challenge_method=plain"
    
  val tokenUrl = "https://api.twitter.com/2/oauth2/token"

  println(s"clinetId: ${clientId}")
  println(s"clinetSecret: ${clientSecret}")
  println(s"basicAuth: ${basicAuth}")

  // println("Requesting Twitter OpenID configuration...")
  // val config = requests.get("https://accounts.google.com/.well-known/openid-configuration")
  // println("Requesting Twitter OAuth2 JWKS...")
  // val jwks = requests.get("https://www.googleapis.com/oauth2/v3/certs")
  // val jwksJson = ujson.read(jwks.text)
  // println(s"Twitter OAuth2 JWKS:\n${jwksJson}")

  @cask.get("/")
  def index() = {
    cask.Redirect("/login")
  }

  @cask.get("/login")
  def login() = {
      html(
        body(
          h1("Login"),
          a(href:=loginUrl)(
            p("Twitter")
          )

        )
      )
  }

  def renderResult(accessToken:String,jwt:String,jwtList:Map[String,String],profile:Map[String,String]) = {
    html(
      body(
          h3("OpenID Connect"),
          h4(s"access_token: ${accessToken}"),
          h4(s"jwt: ${jwt}"),
          for((k,v) <- jwtList.toSeq) yield div(
            div(k+": "+v),
            if(k=="picture" || k=="profile_image_url") {
              img(src:=v)
            },
            // (br)
          ),
          h3("Profile:"),
          for((k,v) <- profile.toSeq) yield div(
            div(k+": "+v),
            if(k=="picture" || k=="profile_image_url") {
              img(src:=v)
            },
            // (br)
          )
      ) 
    )
  }

  def claimToStrMap(jwt:scala.util.Try[JwtClaim]):Map[String,String] = 
    ujson.read(jwt.get.toJson).obj.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap

  @cask.get("/callback/")
  def callaback(code: String,
                scope: Option[String] = None,
                state: Option[String]=None,
                prompt: Option[String]=None,
                authuser: Option[String]=None,
                hd: Option[String]=None) = {
    println(s"\ncode=${code}\nscope=${scope}")

    val r = requests.post(tokenUrl, 
      headers = Map(
        "Authorization" -> s"Basic ${basicAuth}",
      ),
      data = Map(
        "code" -> code,
        "client_id" -> clientId,
        "redirect_uri" -> redirectUri,
        "grant_type" -> "authorization_code",
        "code_verifier" -> "challenge"
      )
    )

    println(s"${r}")

    val json = ujson.read(r.text)
    val access_token = json.obj("access_token").str
    val id_token = ""

    println(s"access_token: ${access_token}\nid_token=${id_token}\n")

    // call API profile on myself
    val profileRsp = requests.get(s"https://api.twitter.com/2/users/me?user.fields=id,name,profile_image_url,location,created_at,url,description,entities,public_metrics",
                                  headers = Map("Authorization" -> s"Bearer ${access_token}"))
    
    val profile = ujson.read(profileRsp.text).obj("data").obj

    println(s"profile: ${profile}")

    renderResult(access_token,id_token,Map(),profile.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap)
  }

  // def verify(jwtStr:String,index:Int= -1):Seq[String] = {
    
  //   val jwk = JWKSet.parse(jwks.text) 
  //   val matcher = (new JWKMatcher.Builder).publicOnly(true).keyType(KeyType.RSA).keyUse(KeyUse.SIGNATURE).build
    
  //   jwksJson.obj("keys").arr.view.zipWithIndex.collect{ case(o,i) if(index== -1 || i==index) => {
  //     val alg = o.obj("alg")
  //     val n = o.obj("n")
  //     println(s"JWKS: alg=${alg},n=${n}")

  //     // create Public Key from JKS
  //     val publicKey = (new JWKSelector(matcher)).select(jwk).get(i).asInstanceOf[RSAKey].toPublicKey
     
  //     val jwt = Jwt.decode(jwtStr,publicKey,JwtAlgorithm.allRSA)
  //     println(s"JWT Verification: ${jwt}")
  //     s"${i}: JWK.n=${n}: JWK.publicKey=${publicKey}: ${jwt.toString}\n==================================\n"
  //   }}.toSeq
  // }

  println(s"Listening on ${port}...")
  initialize()
}
