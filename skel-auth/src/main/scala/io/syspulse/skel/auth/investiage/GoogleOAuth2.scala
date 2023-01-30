package io.syspulse.skel.auth.investigate

import requests._
import scalatags.Text.all._
import scalatags.Text.tags2
import java.time.Clock
import pdi.jwt.{Jwt, JwtAlgorithm, JwtHeader, JwtClaim, JwtOptions}
import com.nimbusds.jose.jwk._
object GoogleOAuth2 extends cask.MainRoutes{
  override def port = 3001

  val clientId = Option(System.getenv("AUTH_CLIENT_ID")).getOrElse("UNKNOWN")
  val clientSecret = Option(System.getenv("AUTH_CLIENT_SECRET")).getOrElse("UNKNOWN")
  val redirectUri = "http://localhost:3001/callback"
  
  val loginUrl=
    s"https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=${clientId}&scope=openid profile email&redirect_uri=${redirectUri}"
  val tokenUrl = "https://oauth2.googleapis.com/token"

  val TEST_JSON = """
      {
  "access_token": "ya29.a0Ae4lvC0fWPskBUlhBF6-QTp5pX4wR_HSMqGN5-_kpyyomS1iPcIwQ6H59EwqkGAzMDg7NvsYlRnQyMaqOzpsutuudqZ4LxCyL_GcKLsmIgn5M9u6pu_NrTPv9cLEW5Kr0A11d4OVa1J3uedatoXWhhMFpWvY_m86FSI",
  "expires_in": 3599,
  "scope": "openid",
  "token_type": "Bearer",
  "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6IjZmY2Y0MTMyMjQ3NjUxNTZiNDg3NjhhNDJmYWMwNjQ5NmEzMGZmNWEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiIxMDg0MDM5NzQ3Mjc2LTNuYTU5a2NyYzhhYjVrNjVsb3V2ZzVqdjh1bG5tdjU0LmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwiYXVkIjoiMTA4NDAzOTc0NzI3Ni0zbmE1OWtjcmM4YWI1azY1bG91dmc1anY4dWxubXY1NC5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsInN1YiI6IjExNjM4NDY2NTgzMzc2NjA4MDQzNCIsImF0X2hhc2giOiJKNnRZNXlINHdIdDFIVktyTTNBNjZRIiwiaWF0IjoxNTg2Njk0NzIzLCJleHAiOjE1ODY2OTgzMjN9.E8HPiQ6DlPbqUS4tFw-IIBNE5EE9T0Rwdyh7yFmLXLrgjrU1Pp9x2r-4THnbHJCZbjVqm8EXTgUHn1u5Ne5V25McY01VdPziNqmc78XC6sesdEiTqMT4I0J1d73d1eJXemuYfQjiVPCm7cVqTTqCoFR_B4fXidTuAljtmI979wNgLmWEuiUwxyYGyQKyBT-aVMdpskGpwMXcJ27onyA6-2ym4Jk3XzzAktIBYkCrQvpYTxIuvDFaZgsiSYe5yWhDRFGjxJgjW_FECK6OqqSM7NQrN58DRBaxvsSBlnxYoaQ0pvXXSYRflJvxHuUoF3bYKBjzL9YI9rxhV3BN8k6Nhg"
}"""

  val TEST_JWT="eyJhbGciOiJSUzI1NiIsImtpZCI6IjZmY2Y0MTMyMjQ3NjUxNTZiNDg3NjhhNDJmYWMwNjQ5NmEzMGZmNWEiLCJ0eXAiOiJKV1QifQ.eyJpc3MiOiJodHRwczovL2FjY291bnRzLmdvb2dsZS5jb20iLCJhenAiOiIxMDg0MDM5NzQ3Mjc2LTNuYTU5a2NyYzhhYjVrNjVsb3V2ZzVqdjh1bG5tdjU0LmFwcHMuZ29vZ2xldXNlcmNvbnRlbnQuY29tIiwiYXVkIjoiMTA4NDAzOTc0NzI3Ni0zbmE1OWtjcmM4YWI1azY1bG91dmc1anY4dWxubXY1NC5hcHBzLmdvb2dsZXVzZXJjb250ZW50LmNvbSIsInN1YiI6IjExNjM4NDY2NTgzMzc2NjA4MDQzNCIsImVtYWlsIjoiYW5kcml5a0BnbWFpbC5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiYXRfaGFzaCI6Im9LS3lPNVZ1VUhXbUxhZGlRNFoxRnciLCJuYW1lIjoiQW5kcml5IEsiLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDMuZ29vZ2xldXNlcmNvbnRlbnQuY29tL2EtL0FPaDE0R2kzcloyVWxfbkw1RVpvUlkzeHdjXzR4S2J3MTMxWlROSU5Id2xQanFRPXM5Ni1jIiwiZ2l2ZW5fbmFtZSI6IkFuZHJpeSIsImZhbWlseV9uYW1lIjoiSyIsImxvY2FsZSI6ImRlIiwiaWF0IjoxNTg2NzY2Njk1LCJleHAiOjE1ODY3NzAyOTV9.dd3jla-ELKWiIGzFzUH8jp0uIv4ROjGIDH2gmGFIZ58htgF8Lw6hb85MwNv7DQe7WIAt2rTUJ7HZJMLKTbJTvKAj1SYTZJ9tn6snoAcUoYYCRXtm1-rG3YSv8WwI3B1OPGai6YlCVD6EWMoL9jw_ZHyh6l-LW_7WLmZuMpT3yQUQGvl-XyG69a73Zpueum6hUjacTTq2niSeyXpU35N2ti6dBqrQi_mw4QuYTSm8QHJbf6nD4I1O21mMJlZMtP8tVI6SYnK4k8sqk3KEatP1kSjNDLuhpqGfETQ9xoVwPmNMCQYx_gAki_4L8ntzUES7MJvnHfkiVjq2yVSuktsG5A"

  println("Requesting Google OpenID configuration...")
  val config = requests.get("https://accounts.google.com/.well-known/openid-configuration")
  println("Requesting Google OAuth2 JWKS...")
  val jwks = requests.get("https://www.googleapis.com/oauth2/v3/certs")
  val jwksJson = ujson.read(jwks.text())
  println(s"Google OAuth2 JWKS:\n${jwksJson}")

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
            p("Google")
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
  def callaback(code: String,
                scope: String,
                state: Option[String]=None,
                prompt: Option[String]=None,
                authuser: Option[String]=None,
                hd: Option[String]=None) = {
    println(s"\ncode=${code}\nscope=${scope}")

    val r = requests.post(tokenUrl, 
      data = Map(
        "code" -> code,
        "client_id" -> clientId,
        "client_secret" -> clientSecret,
        "redirect_uri" -> redirectUri,
        "grant_type" -> "authorization_code"
      )
    )

    println(s"${r}")

    val json = ujson.read(r.text())
    val access_token = json.obj("access_token").str
    val id_token = json.obj("id_token").str

    println(s"access_token: ${access_token}\nid_token=${id_token}\n")

    val jwt = Jwt.decode(id_token,JwtOptions(signature = false))
    println(s"jwt: ${jwt.get.content}")

    // call non OpenID Connect profile
    val profileRsp = requests.get(s"https://www.googleapis.com/oauth2/v1/userinfo?alt=json&access_token=${access_token}")
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

  
  @cask.get("/jwks/:index")
  def jwksTest(index:Int) = {
    val json = ujson.read(TEST_JSON)
    val access_token = json.obj("access_token").str
    val jwtStr = json.obj("id_token").str

    println(s"access_token: ${access_token}\njwt=${jwtStr}")
    verify(jwtStr,index).mkString
  }

  @cask.get("/jwks-all")
  def jwksTestAll() = {
    val json = ujson.read(TEST_JSON)
    val access_token = json.obj("access_token").str
    val jwtStr = json.obj("id_token").str

    println(s"access_token: ${access_token}\njwt=${jwtStr}")
    verify(jwtStr).mkString
  }

  @cask.get("/test/:jwt")
  def test(jwt: Option[String] = Some(TEST_JWT), hd:Option[String]=None) = {
    
    println(s"jwt: ${jwt.get}\n")
    val jwtClaims = Jwt.decode(jwt.get,JwtOptions(signature = false))
    renderResult("",jwt.get,claimToStrMap(jwtClaims),Map())
  }

  println(s"Listening on ${port}...")
  initialize()
}
