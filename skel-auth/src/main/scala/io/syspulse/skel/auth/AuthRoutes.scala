package io.syspulse.skel.auth

import io.jvm.uuid._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ StatusCodes, HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Directive1 }
import akka.http.scaladsl.server.directives.Credentials

import scala.concurrent.Future
import akka.actor.TypedActor
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger

import scala.util.{Success, Try, Failure}

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

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._

import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.AuthRegistry._
import io.syspulse.skel.service.Routeable
import io.syspulse.skel.auth.oauth2.{ OAuthProfile, GoogleOAuth2, TwitterOAuth2, ProxyM2MAuth, EthProfile}
import io.syspulse.skel.auth.oauth2.EthTokenReq
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.server.MissingQueryParamRejection
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import akka.actor
import akka.stream.Materializer
import scala.util.Random
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.oauth2.EthOAuth2
import io.syspulse.skel.crypto.Eth

import io.syspulse.skel.auth.code.CodeRegistry._
import io.syspulse.skel.auth.code._

import io.syspulse.skel
import java.time.LocalDateTime
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

sealed trait AuthResult {
  //def token: Option[OAuth2BearerToken] = None
}
case class BasicAuthResult(token: String) extends AuthResult
case class ProxyAuthResult(token: String) extends AuthResult
case object AuthDisabled extends AuthResult

final case class AuthWithProfileRsp(auid:String, idToken:String, email:String, name:String, avatar:String, locale:String)

object AuthRoutesJsons {
  
  implicit val jf_AuthWithProfileRsp = jsonFormat6(AuthWithProfileRsp)
  implicit val jf_ProxyAuthResult = jsonFormat1(ProxyAuthResult)
}    

class AuthRoutes(authRegistry: ActorRef[skel.Command],serviceUri:String,redirectUri:String)(implicit context:ActorContext[_],config:Config) extends Routeable  {
  val log = Logger(s"${this}")
  implicit val system: ActorSystem[_] = context.system
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  val corsAllow = CorsSettings(system.classicSystem).withAllowGenericHttpRequests(true)
  implicit val timeout = Timeout.create(system.settings.config.getDuration("auth.routes.ask-timeout"))
  
  val codeRegistry: ActorRef[skel.Command] = context.spawn(CodeRegistry(),"Actor-CodeRegistry")
  context.watch(codeRegistry)
  
  val idps = Map(
    GoogleOAuth2.id -> (new GoogleOAuth2(redirectUri)).withJWKS(),
    TwitterOAuth2.id -> (new TwitterOAuth2(redirectUri)),
    ProxyM2MAuth.id -> (new ProxyM2MAuth(redirectUri,config)),
    EthOAuth2.id -> (new EthOAuth2(serviceUri)),
  )
  log.info(s"idps: ${idps}")

  import AuthJson._
  import AuthRoutesJsons._

  def getAuths(): Future[Auths] = authRegistry.ask(GetAuths)

  def getAuth(auid: String): Future[GetAuthRsp] =
    authRegistry.ask(GetAuth(auid, _))

  def createAuth(auth: Auth): Future[CreateAuthRsp] =
    authRegistry.ask(CreateAuth(auth, _))

  def deleteAuth(auid: String): Future[AuthRegistry.ActionRsp] =
    authRegistry.ask(DeleteAuth(auid, _))

  def createCode(code: Code): Future[CreateCodeRsp] = codeRegistry.ask(CreateCode(code, _))
  def getCode(authCode: String): Future[GetCodeRsp] = codeRegistry.ask(GetCode(authCode, _))

  
  def getCallback(idp: Idp, code: String, redirectUri:Option[String],scope: Option[String], state:Option[String]): Future[AuthWithProfileRsp] = {
    log.info(s"code=${code}, redirectUri=${redirectUri}, scope=${scope}, state=${state}")
    
    val data = Map(
      "code" -> code,
      "client_id" -> idp.getClientId,
      "client_secret" -> idp.getClientSecret,
      "redirect_uri" -> redirectUri.getOrElse(idp.getRedirectUri()),
      "grant_type" -> "authorization_code"
    ) ++ idp.getGrantHeaders()

    val basicAuth = idp.getBasicAuth()
    val headers = Seq[HttpHeader]() ++ {if(basicAuth.isDefined) Seq(RawHeader("Authorization",s"Basic ${basicAuth.get}")) else Seq()}

    for {
      tokenReq <- {
        log.info(s"code=${code}: requesting access_token:\n${headers}\n${data}")
        val rsp = Http().singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = idp.getTokenUrl(),
            //entity = HttpEntity(ContentTypes.`application/json`,data)
            entity = FormData(data).toEntity,
            headers = headers
        ))
        rsp
      }
      tokenRsp <- {
        tokenReq.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      }
      idpTokens <- {
        log.info(s"tokenRsp: ${tokenRsp.utf8String}")
        //Unmarshal(tokenRsp).to[GoogleTokens]
        idp.decodeTokens(tokenRsp)
      }
      profileReq <- {
        log.info(s"code=${code}: tokens=${idpTokens}: requesting user profile")
        val (uri,headers) = idp.getProfileUrl(idpTokens.accessToken)
        val rsp = Http().singleRequest(
          HttpRequest(uri = uri)
            .withHeaders(headers.map(h => RawHeader(h._1,h._2)))
            .withEntity(ContentTypes.`application/json`,"")
        )
        rsp
      }
      profileRes <- {
        profileReq.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      }
      profile <- {
        val profile = profileRes.utf8String
        log.info(s"code=${code}: profile: ${profile}")

        idp.decodeProfile(profileRes)
      }
      db <- {
        val auth = Auth(auid = profile.id, idpTokens.accessToken, idpTokens.idToken, scope, idpTokens.expiresIn)
        
        if(auth.idToken != "") {
          val jwt = AuthJwt.decode(auth)
          val claims = AuthJwt.decodeClaim(auth)
          // verify just for logging
          val verified = idp.verify(idpTokens.idToken)
          log.info(s"code=${code}: profile=${profile}: auth=${auth}: jwt=${jwt.get.content}: claims=${claims}: verified=${verified}")
        } else {
          log.info(s"code=${code}: profile=${profile}: auth=${auth}")
        }

        createAuth(auth)
      }
      authRes <- {
        Future(AuthWithProfileRsp(db.auth.auid, db.auth.idToken, profile.email, profile.name, profile.picture, profile.locale))
      }
    } yield authRes
    
  }

  protected def basicAuthCredentials(creds: Credentials)(implicit up: (String,String)):Option[AuthResult] = {
    creds match {
      case p @ Credentials.Provided(id) if up._1.equals(id) && p.verify(up._2) => {
        log.info(s"Authenticated: ${up}")
        Some(BasicAuthResult(id))
      }
      case _ =>
        log.warn(s"Not authenticated: ${up}")
        None
    }
  }

  protected def proxyAuthCredentials(request:HttpRequest)(implicit config:Config):Directive1[AuthResult] = {
    val idp = idps(ProxyM2MAuth.id).asInstanceOf[ProxyM2MAuth]
    val rsp = for {
      body <- request.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      rsp <- { 
        idp.askAuth(request.headers,body.utf8String)
      }
    } yield rsp

    Await.result(rsp, FiniteDuration(1000L, TimeUnit.MILLISECONDS)) match {
      case Success(t) => provide(ProxyAuthResult(t.token))
      case Failure(e) => {
        log.warn(s"Not authenticated: ${request}")
        complete(StatusCodes.Unauthorized)
      }
    }
  }

  protected def authenticateBasicAuth[T]()(implicit config:Config): Directive1[AuthResult] = {
    log.info("Authenticating: Basic-Authentication...")
    implicit val credConfig:(String,String) = (config.authBasicUser,config.authBasicPass)
    authenticateBasic(config.authBasicRealm, basicAuthCredentials)
  }

  protected def authenticateProxyAuth[T](request:HttpRequest)(implicit config:Config): Directive1[AuthResult] = {
    log.info(s"Authenticating: Proxy M2M... (request=${request}")
    proxyAuthCredentials(request)
  }

  protected def authenticateAll[T]()(implicit config:Config): Directive1[AuthResult] = {
    authenticateBasicAuth()
  }

  override val routes: Route = cors() {
    concat(
      // simple embedded Login FrontEnd
      path("login") {
        // getFromResourceDirectory("login") 
        // getFromResource("login/index.html")
        complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,
        s"""
        <html>
<head>
</head>
<body>
  <h1>skel-auth</h1>
  <a href="${idps.get(GoogleOAuth2.id).get.getLoginUrl()}">Google</a>
  <br>
  <a href="${idps.get(TwitterOAuth2.id).get.getLoginUrl()}">Twitter</a>
  <br>
  <a href="${idps.get(EthOAuth2.id).get.getLoginUrl()}">Web3 (Eth)</a><b>You must login within 5 seconds after refresh</b>
</body>
</html>
        """
        ))
      },
      // token is requested from FrontEnt with authorization code 
      pathPrefix("token") {
        path("google") {
          pathEndOrSingleSlash {
            get {
              parameters("code", "redirect_uri".optional, "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,redirectUri,scope,state,prompt,authuser,hd) =>
                onSuccess(getCallback(idps.get(GoogleOAuth2.id).get,code,redirectUri,scope,state)) { rsp =>
                  complete(StatusCodes.Created, rsp)
                }
              }
            }
          }
        } 
      },
      // this is internal flow, not compatible with FrontEnd flow
      pathPrefix("callback") {
        path("google") {
          pathEndOrSingleSlash {
            get {
              parameters("code", "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,scope,state,prompt,authuser,hd) =>
                onSuccess(getCallback(idps.get(GoogleOAuth2.id).get,code,None,scope,state)) { rsp =>
                  //redirect("",StatusCodes.PermanentRedirect)
                  complete(StatusCodes.Created, rsp)
                }
              }
            }
          }
        } ~
        path("twitter") {
          pathEndOrSingleSlash {
            get {
              parameters("code", "scope".optional,"state".optional) { (code,scope,state) =>
                onSuccess(getCallback(idps.get(TwitterOAuth2.id).get,code,None,scope,None)) { rsp =>
                  complete(StatusCodes.Created, rsp)
                }
              }
            }
          }
        }
      },
      pathPrefix("eth") { 
        path("auth") {
          get {
            parameters("sig".optional,"addr".optional,"redirect_uri", "response_type".optional,"client_id".optional,"scope".optional,"state".optional) { 
              (sig,addr,redirect_uri,response_type,client_id,scope,state) => {
        
                if(!addr.isDefined ) {
                  log.error(s"sig=${sig}, addr=${addr}: invalid signing address")
                  complete(StatusCodes.Unauthorized,"invalid signing address")
                } else {

                  // TODO: CHANGE IT
                  val sigData = EthOAuth2.generateSigData(Map("address" -> addr.get))
                  log.info(s"sigData=${sigData}")

                  val pk = if(sig.isDefined) Eth.recoverMetamask(sigData,Util.fromHexString(sig.get)) else Failure(new Exception(s"Empty signature"))
                  val addrFromSig = pk.map(p => Eth.address(p))

                  if(addrFromSig.isFailure || addrFromSig.get != addr.get.toLowerCase()) {
                    log.error(s"sig=${sig}, addr=${addr}: invalid sig")
                    complete(StatusCodes.Unauthorized,s"invalid sig: ${sig}")
                  } else {

                    val authCode = Util.generateAccessToken()

                    onSuccess(createCode(Code(authCode))) { rsp =>
                      log.info(s"sig=${sig}, addr=${addr}, redirect_uri=${redirect_uri}, code=${authCode}")
                      redirect(redirect_uri + s"?code=${rsp.code.authCode}", StatusCodes.PermanentRedirect)  
                    }
                  }
                }
                
              }
            }
          }
        } ~
        path("callback") {
          get {
            parameters("code", "scope".optional,"state".optional) { (code,scope,state) => 
              log.info(s"code=${code}, scope=${scope}")
              onSuccess(getCallback(idps.get(EthOAuth2.id).get,code,None,scope,None)) { rsp =>
                complete(StatusCodes.Created, rsp)
              }
            }
          }
        } ~
        path("token") {
          import io.syspulse.skel.auth.oauth2.EthOAuth2._
          import io.syspulse.skel.auth.oauth2.EthTokens
          post {
            //entity(as[EthTokenReq]) { req => {
            formFields("code","client_id","client_secret","redirect_uri","grant_type") { (code,client_id,client_secret,redirect_uri,grant_type) => {
              log.info(s"code=${code},client_id=${client_id},client_secret=${client_secret},redirect_uri=${redirect_uri},grant_type=${grant_type}")
              onSuccess(getCode(code)) { rsp =>
                if(! rsp.code.isDefined) {
                  
                  log.error(s"code=${code}: rsp=${rsp}: not found")
                  complete(StatusCodes.Unauthorized,s"code not found: ${code}")

                } else {
                  val accessToken = Util.sha256(rsp.code.get.authCode)
                  log.info(s"code=${code}: rsp=${rsp.code}: accessToken${accessToken}")

                  complete(StatusCodes.OK,EthTokens(accessToken = accessToken, expiresIn = 3600,scope = "",tokenType = ""))
                }
              }  
            }
          }} ~
          get { parameters("code") { (code) => 
            onSuccess(getCode(code)) { rsp =>
              if(! rsp.code.isDefined) {
                
                log.error(s"code=${code}: rsp=${rsp}: not found")
                complete(StatusCodes.Unauthorized,s"code not found: ${code}")

              } else {
                val accessToken = Util.sha256(rsp.code.get.authCode)
                log.info(s"code=${code}: rsp=${rsp.code}: accessToken${accessToken}")

                complete(StatusCodes.OK,EthTokens(accessToken = accessToken, expiresIn = 3600,scope = "",tokenType = ""))
              }
            }
          }}
        } ~
        path("profile") {
          import io.syspulse.skel.auth.oauth2.EthOAuth2._
          get {
            parameters("access_token") { (access_token) => {
              val rsp = EthProfile(UUID.random.toString, "profile.addr", "profile.email", "profile.avatar", LocalDateTime.now().toString)
              complete(StatusCodes.OK,rsp)
            }}
          }
        }
      } ~
      // curl -POST -i -v http://localhost:8080/api/v1/auth/m2m -d '{ "username" : "user1", "password": "password"}'
      pathPrefix("m2m") {
        path("token") {
          import io.syspulse.skel.auth.oauth2.ProxyM2MAuth._
          import io.syspulse.skel.auth.oauth2.ProxyTokensRes

          val rsp = ProxyTokensRes(id = 1,token=Util.generateAccessToken(), refreshToken=Util.generateAccessToken())
          complete(StatusCodes.OK,rsp)
        } ~
        pathEndOrSingleSlash { 
          post {
            extractRequest { request =>
              authenticateProxyAuth(request)(config)(rsp =>
                complete(StatusCodes.OK,rsp.toString)
              )
            }
          }
        }
      } ~
      pathEndOrSingleSlash {
        concat(
          get {
            authenticateAll()(config)(_ => 
              complete(getAuths())  
            )              
          },
          post {
            entity(as[Auth]) { auth =>
              onSuccess(createAuth(auth)) { rsp =>
                complete(StatusCodes.Created, rsp.auth)
              }
            }
          })
      },
      path(Segment) { name =>
        concat(
          get {
            rejectEmptyResponse {
              onSuccess(getAuth(name)) { response =>
                complete(response.auth)
              }
            }
          },
          delete {
            onSuccess(deleteAuth(name)) { rsp =>
              complete((StatusCodes.OK, rsp))
            }
          })
      })
    }
}
