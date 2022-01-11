package io.syspulse.skel.auth

import io.jvm.uuid._

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ StatusCodes, HttpEntity, ContentTypes}
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import com.typesafe.scalalogging.Logger

import scala.util.Success
import scala.util.Try

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
import io.syspulse.skel.auth.oauth2.{ OAuthProfile, GoogleOAuth2, TwitterOAuth2 }
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.headers.RawHeader


final case class AuthWithProfileRsp(auid:String, idToken:String, email:String, name:String, avatar:String, locale:String)

object AuthRoutesJsons {
  
  implicit val authWithProfileFormat = jsonFormat6(AuthWithProfileRsp)
}    

class AuthRoutes(authRegistry: ActorRef[AuthRegistry.Command],redirectUri:String)(implicit val system: ActorSystem[_]) extends Routeable  {
  val log = Logger(s"${this}")
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val idps = Map(
    GoogleOAuth2.id -> (new GoogleOAuth2(redirectUri)).withJWKS(),
    TwitterOAuth2.id -> (new TwitterOAuth2(redirectUri))
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
              //authenticateBasicAsync {
                get {
                  parameters("code", "scope".optional,"state".optional) { (code,scope,state) =>
                    onSuccess(getCallback(idps.get(TwitterOAuth2.id).get,code,scope,None)) { rsp =>
                      complete(StatusCodes.Created, rsp)                  
                    }
                  }
                }
              //}
            }
          }
        },
        pathEndOrSingleSlash {
          concat(
            get {
              complete(getAuths())
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
