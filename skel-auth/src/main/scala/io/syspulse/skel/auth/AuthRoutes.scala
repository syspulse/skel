package io.syspulse.skel.auth

import io.jvm.uuid._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ StatusCodes, HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ Directive, Directive1 }
import akka.http.scaladsl.server.directives.Credentials

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
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


import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.AuthRegistry._
import io.syspulse.skel.service.Routeable
import io.syspulse.skel.auth.oauth2.{ OAuthProfile, GoogleOAuth2, TwitterOAuth2, ProxyM2MAuth}
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

sealed trait AuthResult {
  //def token: Option[OAuth2BearerToken] = None
}
case class BasicAuthResult(token: String) extends AuthResult
case class ProxyAuthResult(token: String) extends AuthResult
case object AuthDisabled extends AuthResult

final case class AuthWithProfileRsp(auid:String, idToken:String, email:String, name:String, avatar:String, locale:String)

object AuthRoutesJsons {
  
  implicit val authWithProfileFormat = jsonFormat6(AuthWithProfileRsp)
  implicit val j1 = jsonFormat1(ProxyAuthResult)
}    

class AuthRoutes(authRegistry: ActorRef[AuthRegistry.Command],redirectUri:String)(implicit val system: ActorSystem[_],config:Config) extends Routeable  {
  val log = Logger(s"${this}")
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val idps = Map(
    GoogleOAuth2.id -> (new GoogleOAuth2(redirectUri)).withJWKS(),
    TwitterOAuth2.id -> (new TwitterOAuth2(redirectUri)),
    ProxyM2MAuth.id -> (new ProxyM2MAuth(redirectUri,config)),
  )
  log.info(s"idps: ${idps}")

  import AuthJson._
  import AuthRoutesJsons._
  
  private implicit val timeout = Timeout.create(system.settings.config.getDuration("auth.routes.ask-timeout"))

  def getAuths(): Future[Auths] = authRegistry.ask(GetAuths)

  def getAuth(auid: String): Future[GetAuthResponse] =
    authRegistry.ask(GetAuth(auid, _))

  def createAuth(auth: Auth): Future[CreateAuthResponse] =
    authRegistry.ask(CreateAuth(auth, _))

  def deleteAuth(auid: String): Future[ActionPerformed] =
    authRegistry.ask(DeleteAuth(auid, _))

  def getCallback(idp: Idp, code: String, scope: Option[String], state:Option[String]): Future[AuthWithProfileRsp] = {
    log.info(s"code=${code}, scope=${scope}, state=${state}")
    
    val data = Map(
      "code" -> code,
      "client_id" -> idp.getClientId,
      "client_secret" -> idp.getClientSecret,
      "redirect_uri" -> idp.getRedirectUri(),
      "grant_type" -> "authorization_code"
    ) ++ idp.getGrantHeaders()

    val basicAuth = idp.getBasicAuth()
    val headers = Seq[HttpHeader]() ++ {if(basicAuth.isDefined) Seq(RawHeader("Authorization",s"Basic ${basicAuth.get}")) else Seq()}

    for {
      tokenReq <- {
        log.info(s"code=${code}: requesting access_code:\n${headers}\n${data}")
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
        Future(AuthWithProfileRsp(db.auth.auid,db.auth.idToken,profile.email,profile.name,profile.picture,profile.locale))
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
        val headers = request.headers.map(h => h.name() -> h.value()).toMap
        idp.askAuth(headers,body.utf8String)
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

  override val routes: Route =
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
</body>
</html>
          """
          ))
        },
        pathPrefix("callback") {
          path("google") {
            pathEndOrSingleSlash {
              get {
                parameters("code", "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,scope,state,prompt,authuser,hd) =>
                  onSuccess(getCallback(idps.get(GoogleOAuth2.id).get,code,scope,state)) { rsp =>
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
                  onSuccess(getCallback(idps.get(TwitterOAuth2.id).get,code,scope,None)) { rsp =>
                    complete(StatusCodes.Created, rsp)                  
                  }
                }
              }
            }
          }
        },
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
              onSuccess(deleteAuth(name)) { performed =>
                complete((StatusCodes.OK, performed))
              }
            })
        })
}
