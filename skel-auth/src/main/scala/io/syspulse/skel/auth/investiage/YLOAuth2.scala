package io.syspulse.skel.auth.investigate

import requests._
import scalatags.Text.all._
import scalatags.Text.tags2
import java.time.Clock
import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import com.nimbusds.jose.jwk._

import java.net.URLEncoder
import java.nio.charset.StandardCharsets 

object YLOAuth2 extends cask.MainRoutes{
  override def port = 3001

  val clientId = Option(System.getenv("AUTH_CLIENT_ID")).getOrElse("dev")
  val clientSecret = Option(System.getenv("AUTH_CLIENT_SECRET")).getOrElse("UNKNOWN")
  //val redirectUri = "https://localhost:5000/api/decode-token"
  val redirectUri = "http://localhost:3001/callback"

  val responseType = "code"
  //val scope="openid yl email profile"
  val scope="openid"
  val returnUrl = "/connect/authorize/callback"
  val locale="en-US"
  

  val loginUrlUnencoded = s"${returnUrl}&response_type=${responseType}&client_id=${clientId}&locale=${locale}&initial_screen&scope=${scope}&redirect_uri=${redirectUri}"
  
  //val loginUrl= "https://localhost:5000/login?returnUrl=" + URLEncoder.encode(loginUrlUnencoded, StandardCharsets.UTF_8.toString())
  //val loginUrl = "https://localhost:5000/login?returnUrl=%2Fconnect%2Fauthorize%2Fcallback%3Fclient_id%3Ddev%26scope%3Dopenid%2520email%2520profile%2520yl%26response_type%3Dcode%26locale%3Den-US%26initial_screen%26redirect_uri%3Dhttps%253A%252F%252Flocalhost%253A5000%252Fapi%252Fdecode-token"
  val loginUrl = "https://localhost:5000/login?returnUrl=%2Fconnect%2Fauthorize%2Fcallback%3Fclient_id%3Ddev%26scope%3Dopenid%2520email%2520profile%2520yl%26response_type%3Dcode%26locale%3Den-US%26initial_screen%26redirect_uri%3Dhttp%253A%252F%252Flocalhost%253A3001%252Fcallback"
    
  val tokenUrl = "https://localhost:5000/connect/token"

  println("Requesting OpenID configuration...")
  val config = requests.get("https://localhost:5000/.well-known/openid-configuration",verifySslCerts=false)
  
  println("Requesting OAuth2 JWKS...")
  val jwks = requests.get("https://localhost:5000/.well-known/openid-configuration/jwks",verifySslCerts=false)
  val jwksJson = ujson.read(jwks.text())
  println(s"OAuth2 JWKS:\n${jwksJson}")

  @cask.get("/")
  def index() = {
    cask.Redirect("/login")
  }

  @cask.get("/login")
  def login() = {
      println("Login ?")
      html(
        body(
          h1("Login"),
          a(href:=loginUrl)(
            p("Universal Login")
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
            if(k=="picture") {
              img(src:=v)
            },
            // (br)
          ),
          h3("Profile:"),
          for((k,v) <- profile.toSeq) yield div(
            div(k+": "+v),
            // (br)
          )
      ) 
    )
  }

  def claimToStrMap(jwt:scala.util.Try[JwtClaim]):Map[String,String] = 
    ujson.read(jwt.get.toJson).obj.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap

  @cask.get("/callback/")
  def callback(code: String,
                scope: String,
                state: Option[String]=None,
                prompt: Option[String]=None,
                authuser: Option[String]=None,
                session_state: Option[String]=None,
                hd: Option[String]=None) = {
    println(s"\ncode=${code},\nscope=${scope},\nsession_state=${session_state}")

    val r = requests.post(tokenUrl, 
      data = Map(
        "code" -> code,
        "client_id" -> clientId,
        "client_secret" -> clientSecret,
        "redirect_uri" -> redirectUri,
        "grant_type" -> "authorization_code"
      ),
      verifySslCerts=false
    )

    println(s"${r}")

    val json = ujson.read(r.text())
    val access_token = json.obj("access_token").str
    val id_token = json.obj("id_token").str

    println(s"access_token: ${access_token}\nid_token=${id_token}\n")

    val jwt = Jwt.decode(id_token,JwtOptions(signature = false))
    println(s"jwt: ${jwt.get.content}")

    // call non OpenID Connect profile
    val profileRsp = requests.get(s"https://localhost:5000/connect/userinfo",
      headers = Map(
        "Authorization" -> s"Bearer ${access_token}"
      ),
      verifySslCerts=false
    )
    val profile = ujson.read(profileRsp.text()).obj
    
    // verify Key with public key
    // verify(jwt.get.content,1)

    renderResult(access_token,id_token,claimToStrMap(jwt),profile.map(v=> v._1 -> v._2.toString.stripSuffix("\"").stripPrefix("\"")).toMap)
  }

  def verify(jwtStr:String,index:Int= -1):Seq[String] = {
    
    val jwk = JWKSet.parse(jwks.text()) 
    val matcher = (new JWKMatcher.Builder).publicOnly(true).keyType(KeyType.RSA).keyUse(KeyUse.SIGNATURE).build
    
    jwksJson.obj("keys").arr.view.zipWithIndex.collect{ case(o,i) if(index== -1 || i==index) => {
      val alg = o.obj("alg")
      val n = o.obj("n")
      println(s"JWKS: alg=${alg},n=${n}")

      // create Public Key from JKS
      val publicKey = (new JWKSelector(matcher)).select(jwk).get(i).asInstanceOf[RSAKey].toPublicKey
     
      val jwt = Jwt.decode(jwtStr,publicKey,JwtAlgorithm.allRSA())
      println(s"JWT Verification: ${jwt}")
      s"${i}: JWK.n=${n}: JWK.publicKey=${publicKey}: ${jwt.toString}\n==================================\n"
    }}.toSeq
  }
  
  println(s"Listening on ${port}...")
  initialize()
}
