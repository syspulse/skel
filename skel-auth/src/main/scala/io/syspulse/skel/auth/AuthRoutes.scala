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
import io.syspulse.skel.auth.oauth2.GoogleOAuth2


final case class AuthWithProfileRsp(auid:String, idToken:String, scope:String, email:String, name:String, avatar:String)
final case class Tokens(accessToken: String,expiresIn:Int, scope:String, tokenType:String, idToken:String)
final case class GoogleProfile(id:String,email:String,name:String,picture:String)

object AuthRoutesJsons {
  implicit val tokenFormat = jsonFormat(Tokens,"access_token","expires_in","scope", "token_type", "id_token")
  implicit val googleProfileFormat = jsonFormat(GoogleProfile,"id","email","name","picture")
  implicit val authWithProfileFormat = jsonFormat6(AuthWithProfileRsp)
}    

class AuthRoutes(authRegistry: ActorRef[AuthRegistry.Command],redirectUri:String)(implicit val system: ActorSystem[_]) extends Routeable  {
  val log = Logger(s"${this}")
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  val idp = (new GoogleOAuth2(redirectUri)).withJWKS()
  log.info(s"idp: ${idp}")

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

  def getCallback(code: String, scope:String, state:Option[String]): Future[AuthWithProfileRsp] = {
    log.info(s"code=${code}, scope=${scope}, state=${state}")
    
    val data = Map(
      "code" -> code,
      "client_id" -> idp.getClientId,
      "client_secret" -> idp.getClientSecret,
      "redirect_uri" -> idp.getRedirectUri,
      "grant_type" -> "authorization_code"
    )

    for {
      tokenReq <- {
        log.info(s"code=${code}: requesting JWT:\n${data}")
        val rsp = Http().singleRequest(
          HttpRequest(
            method = HttpMethods.POST,
            uri = idp.getTokenUrl,
            //entity = HttpEntity(ContentTypes.`application/json`,data)
            entity = FormData(data).toEntity,
        ))
        rsp
      }
      tokenRsp <- {
        tokenReq.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      }
      tokens <- {
        log.info(s"tokenRsp: ${tokenRsp.utf8String}")
        Unmarshal(tokenRsp).to[Tokens]
      }
      // jwt <- {
      //   log.info(s"code=${code}: tokens: ${tokens}");
      //   Future(tokens.accessToken)
      // }
      profileReq <- {
        log.info(s"code=${code}: requesting user...")
        val rsp = Http().singleRequest(
          HttpRequest(uri = idp.profileUrl(tokens.accessToken))
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

        Unmarshal(profileRes).to[GoogleProfile]
      }
      db <- {
        val auth = Auth(auid = profile.id, tokens.accessToken, tokens.idToken, scope, tokens.expiresIn)
        val jwt = AuthJwt.decode(auth)
        val claims = AuthJwt.decodeClaim(auth)
        // verify just for logging
        val verified = idp.verify(tokens.idToken)

        val _s=s"code=${code}: profile=${profile}: auth=${auth}: jwt=${jwt.get.content}: claims=${claims}: verified=${verified}"
        if(verified) log.info(_s) else log.warn(_s)
        
        createAuth(auth)
      }
      authRes <- {
        Future(AuthWithProfileRsp(db.auth.auid,db.auth.idToken,db.auth.scope,profile.email,profile.name,profile.picture))
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
    <a href="${idp.loginUrl}">Google</a>
</body>
</html>
          """
          ))
        },
        path("callback") {
          pathEndOrSingleSlash {
            get {
              parameters("code", "scope", "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,scope,state,prompt,authuser,hd) =>
                onSuccess(getCallback(code,scope,state)) { rsp =>
                  //complete((StatusCodes.OK, response))
                  complete(StatusCodes.Created, rsp)                  
                }
              }
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
