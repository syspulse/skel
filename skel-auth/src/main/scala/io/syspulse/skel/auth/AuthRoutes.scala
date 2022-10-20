package io.syspulse.skel.auth

import java.time.LocalDateTime

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
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

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

import io.syspulse.skel.auth.permissions.rbac.Permissions

import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.oauth2.EthOAuth2
import io.syspulse.skel.crypto.Eth

import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.AuthRegistry._
import io.syspulse.skel.service.Routeable
import io.syspulse.skel.auth.oauth2.{ OAuthProfile, GoogleOAuth2, TwitterOAuth2, ProxyM2MAuth, EthProfile}
import io.syspulse.skel.auth.oauth2.EthTokenReq

import io.syspulse.skel.auth.code.CodeRegistry._
import io.syspulse.skel.auth.code._

import io.syspulse.skel
import io.syspulse.skel.auth.proxy._

import io.syspulse.skel.user.client.UserClientHttp


class AuthRoutes(authRegistry: ActorRef[skel.Command],serviceUri:String,redirectUri:String,serviceUserUri:String)(implicit context:ActorContext[_],config:Config) 
    extends Routeable with RouteAuthorizers {

  implicit val system: ActorSystem[_] = context.system
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  val corsAllow = CorsSettings(system.classicSystem).withAllowGenericHttpRequests(true)
  implicit val timeout = Timeout.create(system.settings.config.getDuration("auth.routes.ask-timeout"))
  
  val codeRegistry: ActorRef[skel.Command] = context.spawn(CodeRegistry(),"Actor-CodeRegistry")
  context.watch(codeRegistry)
  
  // lazy because EthOAuth2 JWKS will request while server is not started yet
  lazy val idps = Map(
    GoogleOAuth2.id -> (new GoogleOAuth2(redirectUri)).withJWKS(),
    TwitterOAuth2.id -> (new TwitterOAuth2(redirectUri)),
    ProxyM2MAuth.id -> (new ProxyM2MAuth(redirectUri,config)),
    EthOAuth2.id -> (new EthOAuth2(serviceUri)).withJWKS(),
  )
  //log.info(s"idps: ${idps}")

  import AuthJson._

  def getAuths(): Future[Auths] = authRegistry.ask(GetAuths)

  def getAuth(auid: String): Future[AuthRes] = authRegistry.ask(GetAuth(auid, _))
  def createAuth(auth: Auth): Future[AuthCreateRes] = authRegistry.ask(CreateAuth(auth, _))
  def deleteAuth(auid: String): Future[AuthActionRes] = authRegistry.ask(DeleteAuth(auid, _))
  def createCode(code: Code): Future[CodeCreateRes] = codeRegistry.ask(CreateCode(code, _))
  def updateCode(code: Code): Future[CodeCreateRes] = codeRegistry.ask(UpdateCode(code, _))
  def getCode(authCode: String): Future[CodeRes] = codeRegistry.ask(GetCode(authCode, _))
  def getCodeByToken(accessToken: String): Future[CodeRes] = codeRegistry.ask(GetCodeByToken(accessToken, _))

  implicit val permissions = Permissions(config.permissionsModel,config.permissionsPolicy)
  // def hasAdminPermissions(authn:Authenticated) = {
  //   val uid = authn.getUser
  //   permissions.isAdmin(uid)
  // }
  
  def callbackFlow(idp: Idp, code: String, redirectUri:Option[String], extraData:Option[Map[String,String]], scope: Option[String], state:Option[String]): Future[AuthWithProfileRes] = {
    log.info(s"code=${code}, redirectUri=${redirectUri}, scope=${scope}, state=${state}")
    
    val data = Map(
      "code" -> code,
      "client_id" -> idp.getClientId,
      "client_secret" -> idp.getClientSecret,
      "redirect_uri" -> redirectUri.getOrElse(idp.getRedirectUri()),
      "grant_type" -> "authorization_code"
    ) ++ idp.getGrantData() ++ extraData.getOrElse(Map())

    val basicAuth = idp.getBasicAuth()
    val headers = Seq[HttpHeader]() ++ {if(basicAuth.isDefined) Seq(RawHeader("Authorization",s"Basic ${basicAuth.get}")) else Seq()}

    for {
      tokenResData <- {
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
        if(tokenResData.status != StatusCodes.OK) 
          Future.failed(new Exception(s"${tokenResData}"))
        else  
          tokenResData.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      }
      idpTokens <- {
        log.info(s"tokenRsp: ${tokenRsp.utf8String}")
        idp.decodeTokens(tokenRsp)
      }
      profileRes <- {        
        val (uri,headers) = idp.getProfileUrl(idpTokens.accessToken)
        log.info(s"code=${code}: tokens=${idpTokens}: requesting user profile -> ${uri}")

        val rsp = Http().singleRequest(
          HttpRequest(uri = uri)
            .withHeaders(headers.map(h => RawHeader(h._1,h._2)))
            .withEntity(ContentTypes.`application/json`,"")
        )
        rsp
      }
      profileResData <- {
        profileRes.entity.dataBytes.runFold(ByteString(""))(_ ++ _)
      }
      profile <- {
        val profile = profileResData.utf8String
        log.info(s"code=${code}: profile: ${profile}")

        idp.decodeProfile(profileResData)
      }
      user <- {
        UserClientHttp(serviceUserUri).withTimeout().getByXidAlways(profile.id)
      }
      authRes <- {        
        val auth = Auth(idpTokens.accessToken, idpTokens.idToken, user.map(_.id), scope, idpTokens.expiresIn)
        
        if(auth.idToken != "") {
          val jwt = AuthJwt.decodeIdToken(auth)
          val claims = AuthJwt.decodeIdClaim(auth)
          // verify just for logging
          val verified = idp.verify(idpTokens.idToken)
          log.info(s"code=${code}: profile=${profile}: auth=${auth}: idToken: jwt=${jwt.get.content}: claims=${claims}: verified=${verified}")
        } else {
          log.info(s"code=${code}: profile=${profile}: auth=${auth}")
        }

        // save Auth Session 
        createAuth(auth)
      }
      authProfileRes <- {
        Future(AuthWithProfileRes(
          authRes.auth.accessToken,
          authRes.auth.idToken,
          authRes.auth.uid,
          profile.id,
          profile.email, 
          profile.name, 
          profile.picture, 
          profile.locale))
      }
    } yield authProfileRes
    
  }

  protected def basicAuthCredentialsProxy(creds: Credentials)(implicit up: (String,String)):Option[Authenticated] = {
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

  protected def proxyAuthCredentials(request:HttpRequest)(implicit config:Config):Directive1[Authenticated] = {
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

  protected def authenticateBasicAuthProxy[T]()(implicit config:Config): Directive1[Authenticated] = {
    log.info("Authenticating: Basic-Authentication...")
    implicit val credConfig:(String,String) = (config.proxyBasicUser,config.proxyBasicPass)
    authenticateBasic(config.proxyBasicRealm, basicAuthCredentialsProxy)
  }

  protected def authenticateProxyAuth[T](request:HttpRequest)(implicit config:Config): Directive1[Authenticated] = {
    log.info(s"Authenticating: Proxy M2M... (request=${request}")
    proxyAuthCredentials(request)
  }
  

  override def routes: Route = cors() {
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
  <a href="${idps.get(EthOAuth2.id).get.getLoginUrl()}">Web3 (Eth)</a><b>You must login within 3600 seconds after refresh</b>
</body>
</html>
        """
        ))
      },
      path("jwks") {
        // getFromResourceDirectory("login") 
        getFromResource("keystore/jwks.json")
        //complete(HttpEntity(ContentTypes.`text/html(UTF-8)`,""))
      },
      // token is requested from FrontEnt with authorization code 
      // FE -> Google -> HTTP REDIRECT -> FE(callback) -> skel-auth(token) -> FE
      pathPrefix("token") {
        path("google") {
          pathEndOrSingleSlash {
            get {
              parameters("code", "redirect_uri".optional, "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,redirectUri,scope,state,prompt,authuser,hd) =>
                onSuccess(callbackFlow(idps.get(GoogleOAuth2.id).get,code,redirectUri,None,scope,state)) { rsp =>
                  complete(StatusCodes.Created, rsp)
                }
              }
            }
          }
        } ~
        path("twitter") {
          pathEndOrSingleSlash {
            get {
              parameters("code", "challenge", "redirect_uri".optional, "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,challenge,redirectUri,scope,state,prompt,authuser,hd) =>
                onSuccess(callbackFlow(idps.get(TwitterOAuth2.id).get,code,redirectUri,Some(Map("code_verifier" -> challenge)),scope,state)) { rsp =>
                  complete(StatusCodes.Created, rsp)
                }
              }
            }
          }
        }
      },
      // this is internal flow, not compatible with FrontEnd flow, used as Callback instead of token request from Client
      // FE -> Google -> HTTP REDIRECT -> skel-auth(callback) -> FE
      pathPrefix("callback") {
        path("google") {
          pathEndOrSingleSlash {
            get {
              parameters("code", "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,scope,state,prompt,authuser,hd) =>
                onSuccess(callbackFlow(idps.get(GoogleOAuth2.id).get,code,None,None,scope,state)) { rsp =>
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
                onSuccess(callbackFlow(idps.get(TwitterOAuth2.id).get,code,None,None,scope,None)) { rsp =>
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

                    val code = Util.generateRandomToken()

                    onSuccess(createCode(Code(code,addr))) { rsp =>
                      log.info(s"sig=${sig}, addr=${addr}, redirect_uri=${redirect_uri}: -> code=${code}")
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
              
              onSuccess( callbackFlow(idps.get(EthOAuth2.id).get,code,None,None,scope,None) ) { rsp =>
                complete(StatusCodes.Created, rsp)
              }
            }
          }
        } ~
        path("token") {
          import io.syspulse.skel.auth.oauth2.EthOAuth2._
          import io.syspulse.skel.auth.oauth2.EthTokens

          def generateTokens(code:String) = {
            onSuccess(getCode(code)) { rsp =>
              if(! rsp.code.isDefined || rsp.code.get.expire < System.currentTimeMillis()) {
                
                log.error(s"code=${code}: rsp=${rsp}: not found or expired")
                complete(StatusCodes.Unauthorized,s"code invalid: ${code}")

              } else {

                // request uid from UserService
                onSuccess(UserClientHttp(serviceUserUri).withTimeout().getByXidAlways(rsp.code.get.eid.get)) { user => 
                
                  if(! user.isDefined ) {
                
                    log.warn(s"code=${code}: user=${user}: not found")
                    //complete(StatusCodes.Unauthorized,s"code invalid: ${code}")
                    
                    // issue temporary token for Enrollment
                    val uid = Util.NOBODY.toString
                    val idToken = AuthJwt.generateIdToken(uid) 
                    val accessToken = AuthJwt.generateAccessToken(Map( "uid" -> uid.toString)) 
                    log.info(s"code=${code}: rsp=${rsp.code}: uid=${uid}: accessToken${accessToken}, idToken=${idToken}")

                    onSuccess(updateCode(Code(code,None,Some(accessToken),0L))) { rsp =>                      
                      complete(StatusCodes.OK,EthTokens(accessToken = accessToken, idToken = idToken, expiresIn = 60,scope = "",tokenType = ""))
                    }

                  } else  {

                    val uid = user.get.id

                    val idToken = AuthJwt.generateIdToken(rsp.code.get.eid.getOrElse("")) 
                    val accessToken = AuthJwt.generateAccessToken(Map( "uid" -> uid.toString)) 
                    log.info(s"code=${code}: rsp=${rsp.code}: uid=${uid}: accessToken${accessToken}, idToken=${idToken}")

                    // associate idToken with code for later Profile retrieval by rewriting Code and
                    // immediately expiring code 
                    // Extracting user id possible from JWT 
                    onSuccess(updateCode(Code(code,None,Some(accessToken),0L))) { rsp =>
                      
                      complete(StatusCodes.OK,EthTokens(accessToken = accessToken, idToken = idToken, expiresIn = 3600,scope = "",tokenType = ""))                    
                    }
                  }
                }
                
              }
            }
          }

          post {
            //entity(as[EthTokenReq]) { req => {
            formFields("code","client_id","client_secret","redirect_uri","grant_type") { (code,client_id,client_secret,redirect_uri,grant_type) => {
              log.info(s"code=${code},client_id=${client_id},client_secret=${client_secret},redirect_uri=${redirect_uri},grant_type=${grant_type}")
              
              generateTokens(code)
            }
          }} ~
          get { parameters("code") { (code) => 
            generateTokens(code)
          }}
        } ~
        path("profile") {
          import io.syspulse.skel.auth.oauth2.EthOAuth2._
          get {
            parameters("access_token") { (access_token) => {
              // validate
              if( !AuthJwt.isValid(access_token)) {

                log.error(s"access_token=${access_token}: JWT validation failed")
                complete(StatusCodes.Unauthorized,s"access_token invalid: ${access_token}")

              } else {
                // request from temporary Code Cache
                onSuccess(getCodeByToken(access_token)) { rsp => 

                  // extract uid from AccessToken
                  val uid = AuthJwt.getClaim(access_token,"uid")
                  val eid = rsp.code.get.eid.get
                  complete(StatusCodes.OK,
                    EthProfile( rsp.code.get.eid.get, eid , "profile.email", "profile.avatar", LocalDateTime.now().toString)
                  )                      
                }             
              }
            }}
          }
        }
      } ~
      // curl -POST -i -v http://localhost:8080/api/v1/auth/m2m -d '{ "username" : "user1", "password": "password"}'
      pathPrefix("m2m") {
        path("token") {
          import io.syspulse.skel.auth.oauth2.ProxyM2MAuth._
          import io.syspulse.skel.auth.oauth2.ProxyTokensRes

          val rsp = ProxyTokensRes(id = 1,token=Util.generateRandomToken(), refreshToken=Util.generateRandomToken())
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
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn)) {
                complete(getAuths())
              }
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
      path(Segment) { auid =>
        authenticate()( authn => {
          log.info(s"AuthN: ${authn}")
          concat(
            get {
              rejectEmptyResponse {
                onSuccess(getAuth(auid)) { rsp =>
                  complete(rsp.auth)
                }
              }
            },
            delete {
              onSuccess(deleteAuth(auid)) { rsp =>
                complete((StatusCodes.OK, rsp))
              }
            })
        })
      }
    )}
}
