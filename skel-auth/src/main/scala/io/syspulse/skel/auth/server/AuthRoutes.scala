package io.syspulse.skel.auth.server

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

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import jakarta.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces, PUT}
import jakarta.ws.rs.core.MediaType

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

import io.syspulse.skel.crypto.Eth
import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes

import io.syspulse.skel.auth.jwt.AuthJwt
import io.syspulse.skel.auth.store.AuthRegistry._

import io.syspulse.skel.auth.oauth2.{ OAuthProfile, GoogleOAuth2, TwitterOAuth2, ProxyM2MAuth, EthProfile}
import io.syspulse.skel.auth.oauth2.EthTokenReq

import io.syspulse.skel.auth.code.CodeRegistry._
import io.syspulse.skel.auth.code._

import io.syspulse.skel
import io.syspulse.skel.auth.proxy._

import io.syspulse.skel.user.client.UserClientHttp

import io.syspulse.skel.auth.oauth2.EthOAuth2
import io.syspulse.skel.auth.oauth2.EthOAuth2._
import io.syspulse.skel.auth.oauth2.EthTokens

import io.syspulse.skel.auth.server.AuthJson
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.auth.store.AuthRegistry

import io.syspulse.skel.auth.oauth2.Idp

import io.syspulse.skel.auth._

import io.syspulse.skel.auth.server.{AuthCreateRes, Auths, AuthActionRes, AuthRes, AuthIdp, AuthWithProfileRes}

import io.syspulse.skel.auth.code._
import io.syspulse.skel.auth.cred._
import io.syspulse.skel.auth.cred.CredRegistry._

@Path("/")
class AuthRoutes(authRegistry: ActorRef[skel.Command],serviceUri:String,redirectUri:String,serviceUserUri:String)(implicit context:ActorContext[_],config:Config) 
    extends CommonRoutes with Routeable with RouteAuthorizers {

  implicit val system: ActorSystem[_] = context.system
  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  //implicit val timeout = Timeout.create(system.settings.config.getDuration("auth.routes.ask-timeout"))
  
  val codeRegistry: ActorRef[skel.Command] = context.spawn(CodeRegistry(),"Actor-CodeRegistry")
  context.watch(codeRegistry)

  val clientRegistry: ActorRef[skel.Command] = context.spawn(CredRegistry(),"Actor-ClietnRegistry")
  context.watch(clientRegistry)
  
  // lazy because EthOAuth2 JWKS will request while server is not started yet
  lazy val idps = Map(
    GoogleOAuth2.id -> (new GoogleOAuth2(redirectUri)).withJWKS(),
    TwitterOAuth2.id -> (new TwitterOAuth2(redirectUri)),
    ProxyM2MAuth.id -> (new ProxyM2MAuth(redirectUri,config)),
    EthOAuth2.id -> (new EthOAuth2(serviceUri)).withJWKS(),
  )
  //log.info(s"idps: ${idps}")

  import AuthJson._
  import CodeJson._
  import CredJson._

  def getAuths(): Future[Auths] = authRegistry.ask(GetAuths)

  def getAuth(auid: String): Future[Try[Auth]] = authRegistry.ask(GetAuth(auid, _))
  def createAuth(auth: Auth): Future[AuthCreateRes] = authRegistry.ask(CreateAuth(auth, _))
  def deleteAuth(auid: String): Future[AuthActionRes] = authRegistry.ask(DeleteAuth(auid, _))
  def refreshTokenAuth(auid: String, refreshToken:String, uid:Option[UUID]): Future[Try[Auth]] = authRegistry.ask(RefreshTokenAuth(auid,refreshToken,uid, _))

  def createCode(code: Code): Future[CodeCreateRes] = codeRegistry.ask(CreateCode(code, _))
  def updateCode(code: Code): Future[CodeCreateRes] = codeRegistry.ask(UpdateCode(code, _))
  def getCode(code: String): Future[Try[Code]] = codeRegistry.ask(GetCode(code, _))
  def getCodeByToken(accessToken: String): Future[CodeRes] = codeRegistry.ask(GetCodeByToken(accessToken, _))
  def getCodes(): Future[Try[Codes]] = codeRegistry.ask(GetCodes(_))

  def getCreds(): Future[Try[Creds]] = clientRegistry.ask(GetCreds)
  def getCred(id: String): Future[Try[Cred]] = clientRegistry.ask(GetCred(id, _))
  def createCred(req: CredCreateReq): Future[Try[Cred]] = clientRegistry.ask(CreateCred(req, _))
  def deleteCred(id: String): Future[CredActionRes] = clientRegistry.ask(DeleteCred(id, _))

  implicit val permissions = Permissions(config.permissionsModel,config.permissionsPolicy)
  // def hasAdminPermissions(authn:Authenticated) = {
  //   val uid = authn.getUser
  //   permissions.isAdmin(uid)
  // }
  
  def callbackFlow(idp: Idp, code: String, redirectUri:Option[String], extraData:Option[Map[String,String]], scope: Option[String], state:Option[String]) = {//: Future[AuthWithProfileRes] = {
    log.info(s"code=${code}, redirectUri=${redirectUri}, scope=${scope}, state=${state}")
    
    val data = Map(
      "code" -> code,
      "client_id" -> idp.getClientId,
      "client_secret" -> idp.getClientSecret,
      "redirect_uri" -> redirectUri.getOrElse(idp.getRedirectUri()),
      "grant_type" -> "authorization_code"
    ) ++ idp.getGrantData() ++ extraData.getOrElse(Map()) ++ state.map("state" -> _)

    val basicAuth = idp.getBasicAuth()
    val headers = Seq[HttpHeader]() ++ {if(basicAuth.isDefined) Seq(RawHeader("Authorization",s"Basic ${basicAuth.get}")) else Seq()}

    for {
      tokenResData <- {
        log.info(s"code=${code}: => (${idp.getTokenUrl()}):\n${headers}\n${data}")
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
        UserClientHttp(serviceUserUri).withAccessToken(config.jwtRoleService).withTimeout().findByXidAlways(profile.id)
      }
      authProfileRes <- {        
        val (profileEmail,profileName,profilePicture,profileLocale) = 
          if(idpTokens.idToken != "") {
            val idJwt = AuthJwt.decodeIdToken(idpTokens.idToken)
            val idClaims = AuthJwt.decodeIdClaim(idpTokens.idToken)
            // verify just for logging
            val verified = idp.verify(idpTokens.idToken)
            log.info(s"code=${code}: profile=${profile}: idToken: jwt=${idJwt.get.content}: claims=${idClaims}: verified=${verified}")

            (
                if(profile.email.trim.isEmpty()) idClaims.get("email").getOrElse("") else profile.email,
                if(profile.name.trim.isEmpty()) idClaims.get("name").getOrElse("") else profile.name,
                if(profile.picture.trim.isEmpty()) idClaims.get("avatar").getOrElse("") else profile.picture,
                if(profile.locale.trim.isEmpty()) idClaims.get("locale").getOrElse("") else profile.locale,                
            )
          } else {
            log.info(s"code=${code}: profile=${profile}")
            
            (profile.email, profile.name, profile.picture, profile.locale)
          }

        val (accessToken,idToken,refreshToken) =
          if(user.isDefined) {
            val uid = user.get.id.toString
            (
              AuthJwt.generateAccessToken(Map( "uid" -> uid)),
              Some(AuthJwt.generateIdToken(uid, Map("email" -> profileEmail,"name"->profileName,"avatar"->profilePicture,"locale"->profileLocale ))),
              Some(AuthJwt.generateRefreshToken(uid))
            )
          }
          else {
            (
              AuthJwt.generateAccessToken(Map( "uid" -> Permissions.USER_NOBODY.toString)),
              None,
              None
            )
          }
                
        Future(AuthWithProfileRes(
          accessToken,
          idToken,
          refreshToken,
          idp = AuthIdp(idpTokens.accessToken, idpTokens.idToken, idpTokens.refreshToken),
          uid = user.map(_.id),
          xid = profile.id,
          profileEmail, 
          profileName, 
          profilePicture, 
          profileLocale)
        )
      }
      authRes <- {
        // Save Auth Session        
        createAuth(Auth(
          authProfileRes.accessToken, 
          authProfileRes.idToken, 
          authProfileRes.refreshToken,
          user.map(_.id), 
          scope = Some("api")          
        ))
      }

      // auth <- {        
      //   Future(Auth(
      //     idpTokens.accessToken, 
      //     idpTokens.idToken, 
      //     idpTokens.refreshToken,
      //     user.map(_.id), scope, idpTokens.expiresIn
      //   ))        
      // }
      // authRes <- {
      //   // save Auth Session
      //   createAuth(auth)
      // }
      // authProfileRes <- {
        
      //   val (profileEmail,profileName,profilePicture,profileLocale) = 
      //     if(auth.idToken != "") {
      //       val idJwt = AuthJwt.decodeIdToken(auth)
      //       val idClaims = AuthJwt.decodeIdClaim(auth)
      //       // verify just for logging
      //       val verified = idp.verify(idpTokens.idToken)
      //       log.info(s"code=${code}: profile=${profile}: auth=${auth}: idToken: jwt=${idJwt.get.content}: claims=${idClaims}: verified=${verified}")

      //       (
      //           if(profile.email.trim.isEmpty()) idClaims.get("email").getOrElse("") else profile.email,
      //           if(profile.name.trim.isEmpty()) idClaims.get("name").getOrElse("") else profile.name,
      //           if(profile.picture.trim.isEmpty()) idClaims.get("avatar").getOrElse("") else profile.picture,
      //           if(profile.locale.trim.isEmpty()) idClaims.get("locale").getOrElse("") else profile.locale,                
      //       )
      //     } else {
      //       log.info(s"code=${code}: profile=${profile}: auth=${auth}")
            
      //       (profile.email, profile.name, profile.picture, profile.locale)
      //     }

      //   val (accessToken,idToken,refreshToken) =
      //     if(authRes.auth.uid.isDefined) {
      //       val uid = authRes.auth.uid.get.toString
      //       (
      //         AuthJwt.generateAccessToken(Map( "uid" -> uid)),
      //         Some(AuthJwt.generateIdToken(uid, Map("email" -> profileEmail,"name"->profileName,"avatar"->profilePicture,"locale"->profileLocale ))),
      //         Some(AuthJwt.generateRefreshToken(uid))
      //       )
      //     }
      //     else {
      //       (
      //         AuthJwt.generateAccessToken(Map( "uid" -> Util.NOBODY.toString)),
      //         None,
      //         None
      //       )
      //     }
        
        
      //   Future(AuthWithProfileRes(
      //     accessToken,
      //     idToken,
      //     refreshToken,
      //     AuthIdp(authRes.auth.accessToken, authRes.auth.idToken, authRes.auth.refreshToken),
      //     uid = authRes.auth.uid,
      //     xid = profile.id,
      //     profileEmail, 
      //     profileName, 
      //     profilePicture, 
      //     profileLocale))
      // }
    } yield { 
      // import spray.json._
      // authProfileRes.toJson.prettyPrint
      authProfileRes
    }
        
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
  

  @GET @Path("/token/google") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Get Authentication Profile with Google Code",
    method = "GET",
    parameters = Array(
      new Parameter(name = "code", in = ParameterIn.PATH, description = "code"),
      new Parameter(name = "redirect_uri", in = ParameterIn.PATH, description = "redirect_uri"),
      new Parameter(name = "scope", in = ParameterIn.PATH, description = "scope"),
      new Parameter(name = "state", in = ParameterIn.PATH, description = "state"),
      new Parameter(name = "prompt", in = ParameterIn.PATH, description = "prompt"),
      new Parameter(name = "authuser", in = ParameterIn.PATH, description = "authuser"),
      new Parameter(name = "hd", in = ParameterIn.PATH, description = "hd"),
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Auth returned",content=Array(new Content(schema=new Schema(implementation = classOf[AuthWithProfileRes])))))
  )
  def getTokenGoogle = get {    
    parameters("code", "redirect_uri".optional, "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,redirectUri,scope,state,prompt,authuser,hd) =>
      onSuccess(callbackFlow(idps.get(GoogleOAuth2.id).get,code,redirectUri,None,scope,state)) { rsp =>
        complete(StatusCodes.Created, rsp)
      }
    }
  }

  @GET @Path("/token/twitter") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Get Authentication Profile with Twitter Code",
    method = "GET",
    parameters = Array(
      new Parameter(name = "code", in = ParameterIn.PATH, description = "code"),
      new Parameter(name = "challenge", in = ParameterIn.PATH, description = "challenge"),
      new Parameter(name = "redirect_uri", in = ParameterIn.PATH, description = "redirect_uri"),
      new Parameter(name = "scope", in = ParameterIn.PATH, description = "scope"),
      new Parameter(name = "state", in = ParameterIn.PATH, description = "state"),
      new Parameter(name = "prompt", in = ParameterIn.PATH, description = "prompt"),
      new Parameter(name = "authuser", in = ParameterIn.PATH, description = "authuser"),
      new Parameter(name = "hd", in = ParameterIn.PATH, description = "hd"),
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Authenticated User ID",content=Array(new Content(schema=new Schema(implementation = classOf[AuthWithProfileRes])))))
  )
  def getTokenTwitter = get {
    parameters("code", "challenge", "redirect_uri".optional, "scope".optional, "state".optional, "prompt".optional, "authuser".optional, "hd".optional) { (code,challenge,redirectUri,scope,state,prompt,authuser,hd) =>
      onSuccess(callbackFlow(idps.get(TwitterOAuth2.id).get,code,redirectUri,Some(Map("code_verifier" -> challenge)),scope,state)) { rsp =>
        complete(StatusCodes.Created, rsp)
      }
    }
  }

  @PUT @Path("/refresh/{token}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Refresh token",
    method = "PUT",
    parameters = Array(
      new Parameter(name = "token", in = ParameterIn.PATH, description = "Refresh token"),      
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "New access_token",content=Array(new Content(schema=new Schema(implementation = classOf[Auth])))))
  )
  def putRefreshToken(auid:String,refreshToken:String, uid:Option[UUID]) = put { 
    onSuccess(refreshTokenAuth(auid, refreshToken, uid)) { rsp =>
      complete(StatusCodes.OK, rsp)
    }
  }

  // --------- Eth IDP ------------------------------------------------------------------------------------------------------------------------
  
  @GET @Path("/eth/auth") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Get Authentication Profile with Web3 IDP (Metamask) credentials",
    method = "GET",
    parameters = Array(
      new Parameter(name = "msg", in = ParameterIn.PATH, description = "Message from Metamask encoded in Base64"),
      new Parameter(name = "sig", in = ParameterIn.PATH, description = "Signature of the message (Metmask)"),
      new Parameter(name = "addr", in = ParameterIn.PATH, description = "Ethereum Address which signed the message"),
      new Parameter(name = "redirect_uri", in = ParameterIn.PATH, description = "redirect_uri (/api/v1/auth/eth/callback)"),
      new Parameter(name = "response_type", in = ParameterIn.PATH, description = "response_type (ignored)"),
      new Parameter(name = "client_id", in = ParameterIn.PATH, description = "client_id (ignored)"),
      new Parameter(name = "scope", in = ParameterIn.PATH, description = "scope (ignored)"),
      new Parameter(name = "state", in = ParameterIn.PATH, description = "state (ignored)")      
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Authenticated User ID",content=Array(new Content(schema=new Schema(implementation = classOf[AuthWithProfileRes])))))
  )
  def getAuthEth = get {
    parameters("msg".optional,"sig".optional,"addr".optional,"redirect_uri", "response_type".optional,"client_id".optional,"scope".optional,"state".optional) { 
      (msg,sig,addr,redirect_uri,response_type,client_id,scope,state) => {

        if(!client_id.isDefined ) {
          log.error(s"invalid client_id: ${client_id}")
          complete(StatusCodes.Unauthorized,"invalid client_id")
        } 
        else 
        if(!addr.isDefined ) {
          log.error(s"invalid signing address: addr=${addr}: sig='${sig}'")
          complete(StatusCodes.Unauthorized,"invalid signer")
        } 
        else {
          val client = getCred(client_id.get)
          onSuccess(client) { rsp =>
            rsp match {
              case Success(client) =>
                val sigData = 
                if(msg.isDefined) 
                  // decode from Base64
                  new String(java.util.Base64.getDecoder.decode(msg.get))
                else
                  EthOAuth2.generateSigDataTolerance(Map("address" -> addr.get))

                log.info(s"sigData=${sigData}")

                val pk = if(sig.isDefined) 
                  Eth.recoverMetamask(sigData,Util.fromHexString(sig.get)) 
                else 
                  Failure(new Exception(s"Empty signature"))

                val addrFromSig = pk.map(p => Eth.address(p))

                if(addrFromSig.isFailure || addrFromSig.get != addr.get.toLowerCase()) {
                  log.error(s"sig=${sig}, addr=${addr}: invalid sig")
                  complete(StatusCodes.Unauthorized,s"invalid sig: ${sig}")
                } else {

                  val code = Util.generateRandomToken()

                  onSuccess(createCode(Code(code, addr, state = state))) { rsp =>
                    val redirectUrl = redirect_uri + s"?code=${rsp.code.code}" + {if(state.isDefined) s"&state=${state.get}" else ""}
                    log.info(s"sig=${sig}, addr=${addr}, state=${state}, redirect_uri=${redirect_uri}: -> ${redirectUrl}")
                    redirect(redirectUrl, StatusCodes.PermanentRedirect)  
                  }
                }
              case Failure(e) => 
                log.error(s"invalid client_id: '${client_id.get}'")
                complete(StatusCodes.Unauthorized,"invalid client_id")
            }
          }
        }                
      }
    }
  }

  @GET @Path("/eth/callback") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Web3 Authentication Callback (Server-Side)",
    method = "GET",
    parameters = Array(
      new Parameter(name = "code", in = ParameterIn.PATH, description = "code"),
      new Parameter(name = "scope", in = ParameterIn.PATH, description = "scope"),
      new Parameter(name = "state", in = ParameterIn.PATH, description = "state")
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Authenticated User ID",content=Array(new Content(schema=new Schema(implementation = classOf[AuthWithProfileRes])))))
  )
  def getCallbackEth = get {
    parameters("code", "scope".optional,"state".optional) { (code,scope,state) => 
      log.info(s"code=${code}, scope=${scope}, state=${state}")
      
      onSuccess( callbackFlow(idps.get(EthOAuth2.id).get,code,None,None,scope,state) ) { rsp =>
        complete(StatusCodes.Created, rsp)
      }
    }
  }

  def generateTokens(code:String,state:Option[String],clientId:Option[String],clientSecret:Option[String]) = {
    onSuccess(getCode(code)) { rsp =>
      if(! rsp.isSuccess) {
              
        log.error(s"code=${code}: rsp=${rsp}: code not found")
        complete(StatusCodes.Unauthorized,s"invalid code: ${code}")

      } else if(rsp.get.expire < System.currentTimeMillis()) {
        
        log.error(s"code=${code}: code expired: ${rsp.get.expire}")
        complete(StatusCodes.Unauthorized,s"invalid code: ${code}")

      } else if(!clientId.isDefined ) {
        
        log.error(s"invalid client_id: ${clientId}")
        complete(StatusCodes.Unauthorized,s"invalid client_id")

      } else if(rsp.get.state != state) {

        log.error(s"invalid state: ${state}")
        complete(StatusCodes.Unauthorized,s"invalid state: ${state}")

      } else {
        val client = getCred(clientId.get)
        onSuccess(client) { rspClient =>
          rspClient match {
            case Success(client) =>
              
              if(!clientId.isDefined ) {

                log.error(s"invalid client_id: ${clientId}")
                complete(StatusCodes.Unauthorized,"invalid client_id")
              } else if(Some(client.secret) != clientSecret) {

                log.error(s"invalid client_secret: ${clientSecret}")
                complete(StatusCodes.Unauthorized,"invalid client_secret")
              } else {

                // request uid from UserService
                onSuccess(UserClientHttp(serviceUserUri).withTimeout().findByXidAlways(rsp.get.xid.get)) { user => 
                
                  if(! user.isDefined ) {
                
                    log.warn(s"code=${code}: user=${user}: not found")
                    //complete(StatusCodes.Unauthorized,s"code invalid: ${code}")
                                
                    // non-esisting user
                    val uid = Permissions.USER_NOBODY.toString
                    // issue token for nobody with a scope to start enrollment 
                    val accessToken = AuthJwt.generateAccessToken(Map( "uid" -> uid, "role" -> Permissions.ROLE_NOBODY, "scope" -> "enrollment"))
                    val idToken = ""
                    val refreshToken = ""
                    
                    log.warn(s"code=${code}: rsp=${rsp.get}: uid=${uid}: accessToken${accessToken}, idToken=${idToken}, refreshToken=${refreshToken}")

                    // Update code to become expired !
                    // TODO: Remove code completely !
                    onSuccess(updateCode(Code(code,None,Some(accessToken),None,0L))) { rsp =>                      
                      complete(StatusCodes.OK,
                        EthTokens(
                          accessToken = accessToken, 
                          idToken = idToken, 
                          expiresIn = Auth.NOBODY_AGE,
                          scope = "",
                          tokenType = "",
                          refreshToken = refreshToken
                        ))
                    }

                  } else  {

                    val uid = user.get.id
                    val email = user.get.email
                    val name = user.get.name
                    val avatar = user.get.avatar

                    // generate IDP tokens 
                    val idToken = AuthJwt.generateIdToken(rsp.get.xid.getOrElse(""),Map("email"->email,"name"->name,"avatar"->avatar)) 
                    val accessToken = AuthJwt.generateAccessToken(Map( "uid" -> uid.toString)) 
                    val refreshToken = AuthJwt.generateToken(Map("scope" -> "auth","role" -> "refresh"), expire = 3600L * 10) 
                    
                    log.info(s"code=${code}: rsp=${rsp.get}: uid=${uid}: accessToken${accessToken}, idToken=${idToken}, refreshToken=${refreshToken}")

                    // associate idToken with code for later Profile retrieval by rewriting Code and
                    // immediately expiring code 
                    // Extracting user id possible from JWT 
                    // Update code to make it expired
                    onSuccess(updateCode(Code(code,None,Some(accessToken),None,0L))) { rsp =>
                      
                      complete(StatusCodes.OK,
                        EthTokens(
                          accessToken = accessToken, 
                          idToken = idToken, 
                          expiresIn = Auth.DEF_AGE,
                          scope = "",
                          tokenType = "",
                          refreshToken = refreshToken))
                    }
                  }
                }
              }
            case Failure(e) => 
              log.error(s"invalid client_id: ${e.getMessage()}")
              complete(StatusCodes.Unauthorized,s"client_id: ${e.getMessage()}")
          }
        }        
      }
    }
  }

  @POST @Path("/eth/token") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Web3 Authentication with code",
    method = "POST",
    parameters = Array(
      new Parameter(name = "code", in = ParameterIn.DEFAULT, description = "code"),
      new Parameter(name = "client_id", in = ParameterIn.DEFAULT, description = "client_id"),
      new Parameter(name = "client_secret", in = ParameterIn.DEFAULT, description = "client_secret"),
      new Parameter(name = "redirect_uri", in = ParameterIn.DEFAULT, description = "redirect_uri"),
      new Parameter(name = "grant_type", in = ParameterIn.DEFAULT, description = "grant_type"),
      new Parameter(name = "state", in = ParameterIn.DEFAULT, description = "state (optional)")
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Authenticated User ID",content=Array(new Content(schema=new Schema(implementation = classOf[AuthWithProfileRes])))))
  )
  def postTokenEth = post {
    //entity(as[EthTokenReq]) { req => {
    formFields("code","client_id","client_secret","redirect_uri","grant_type","state".optional) { (code,client_id,client_secret,redirect_uri,grant_type,state) => {
      log.info(s"code=${code},client_id=${client_id},client_secret=${client_secret},redirect_uri=${redirect_uri},grant_type=${grant_type},state=${state}")
      
      generateTokens(code,state,Some(client_id),Some(client_secret))
    }
  }}

  //@GET @Path("/eth/token") @Produces(Array(MediaType.APPLICATION_JSON))
  // @GET @Path("/token/eth") @Produces(Array(MediaType.APPLICATION_JSON))
  // @Operation(tags = Array("auth"),summary = "Web3 Authentication with code",
  //   method = "GET",
  //   parameters = Array(
  //     new Parameter(name = "code", in = ParameterIn.PATH, description = "code"),
  //     new Parameter(name = "state", in = ParameterIn.PATH, description = "state (optional)")
  //   ),
  //   responses = Array(new ApiResponse(responseCode="200",description = "Authenticated User ID",content=Array(new Content(schema=new Schema(implementation = classOf[AuthWithProfileRes])))))
  // )
  // def getTokenEth = get { parameters("code","state".optional) { (code,state) => 
  //   generateTokens(code,state,None,None)
  // }}
  
  @GET @Path("/token/eth") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Web3 Authentication with code",
    method = "GET",
    parameters = Array(
      new Parameter(name = "redirect_uri", in = ParameterIn.PATH, description = "redirect_uri"),
      new Parameter(name = "scope", in = ParameterIn.PATH, description = "scope"),
      new Parameter(name = "state", in = ParameterIn.PATH, description = "state"),
      new Parameter(name = "prompt", in = ParameterIn.PATH, description = "prompt"),
      new Parameter(name = "authuser", in = ParameterIn.PATH, description = "authuser"),
      new Parameter(name = "hd", in = ParameterIn.PATH, description = "hd"),
    ),
    responses = Array(new ApiResponse(responseCode="200",description = "Authenticated User ID",content=Array(new Content(schema=new Schema(implementation = classOf[AuthWithProfileRes])))))
  )
  def getTokenEth = get { parameters("code", "challenge", "redirect_uri".optional, "scope".optional, "state".optional) { (code,challenge,redirectUri,scope,state) =>
    onSuccess(callbackFlow(idps.get(EthOAuth2.id).get,code,redirectUri,None,scope,state)) { rsp =>
      complete(StatusCodes.Created, rsp)
    }}
  }
  
  

// -------- Creds -----------------------------------------------------------------------------------------------------------------------------
  @GET @Path("/client/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Return Client Credentials by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "client_id")),
    responses = Array(new ApiResponse(responseCode="200",description = "Client Credentials returned",content=Array(new Content(schema=new Schema(implementation = classOf[Cred])))))
  )
  def getCredRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getCred(id)) { r =>
        complete(r)
      }
    }
  }

  @GET @Path("/client") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"), summary = "Return all Client Credentials",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Client Crednetials",content = Array(new Content(schema = new Schema(implementation = classOf[Creds])))))
  )
  def getCredsRoute() = get {
    complete(getCreds())
  }

  @DELETE @Path("/client/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Delete Client Credentials by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "client_id")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Cred Credentials deleted",content = Array(new Content(schema = new Schema(implementation = classOf[CredActionRes])))))
  )
  def deleteCredRoute(id: String) = delete {
    onSuccess(deleteCred(id)) { r =>
      complete(StatusCodes.OK, r)
    }
  }

  @POST @Path("/client") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("auth"),summary = "Create Client Credentials",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[CredCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Client Credentials",content = Array(new Content(schema = new Schema(implementation = classOf[Cred])))))
  )
  def createCredRoute = post {
    entity(as[CredCreateReq]) { req =>
      onSuccess(createCred(req)) { r =>
        complete(StatusCodes.Created, r)
      }
    }
  }

// --------------------------------------------------------------------------------------------------------------------------------------- Routes
  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
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
      pathPrefix("client") {
        pathEndOrSingleSlash {
          concat(
            authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getCredsRoute() ~                
                createCredRoute  
              }
            ),            
          )
        } ~        
        pathPrefix(Segment) { id => 
          pathEndOrSingleSlash {
            authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getCredRoute(id) ~
                deleteCredRoute(id)
              }
            ) 
          }
        }
      },
      // token is requested from FrontEnt with authorization code 
      // FE -> Google -> HTTP REDIRECT -> FE(callback) -> skel-auth(token) -> FE
      pathPrefix("token") {
        path("google") {
          pathEndOrSingleSlash {
            getTokenGoogle
            
          }
        } ~
        path("twitter") {
          pathEndOrSingleSlash {
            getTokenTwitter
            
          }
        } ~
        path("eth") {
          pathEndOrSingleSlash {
            getTokenEth
          }
        }
      },
      // this is internal flow, not compatible with FrontEnd flow, used as Callback instead of token request from Cred
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
          getAuthEth
          
        } ~
        path("callback") {
          getCallbackEth
          
        } ~
        path("token") {
          
          postTokenEth ~ getTokenEth          

        } ~
        path("profile") {
          //import io.syspulse.skel.auth.oauth2.EthOAuth2._
          get {
            parameters("access_token") { (access_token) => {
              // validate
              if( !AuthJwt.isValid(access_token)) {

                log.error(s"access_token=${access_token}: JWT validation failed")
                complete(StatusCodes.Unauthorized,s"access_token invalid: ${access_token}")

              } else {
                // request from Code Cache
                onSuccess(getCodeByToken(access_token)) { rsp => 

                  // extract uid from AccessToken
                  val uid = AuthJwt.getClaim(access_token,"uid")
                  val xid = rsp.code.get.xid.get
                  complete(StatusCodes.OK,
                    EthProfile( rsp.code.get.xid.get, "","", "", LocalDateTime.now().toString)
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
      pathPrefix("code") {
        path(Segment) { code => authenticate()( authn => { 
          concat(
            get { rejectEmptyResponse {
              onSuccess(getCode(code)) { rsp => 
                complete(rsp)
            }}}
          )          
        })} ~ 
        pathEndOrSingleSlash {
          authenticate()( authn =>  authorize(Permissions.isAdmin(authn)) {
            complete(getCodes())
          })
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
      } ~
      pathPrefix(Segment) { auid =>
        // refresh token cannot use AuthN because it is expired
        pathPrefix("refresh") {
          pathPrefix(Segment) { refreshToken => 
            putRefreshToken(auid,refreshToken,None)
          }
        } ~
        authenticate()( authn => {
          log.info(s"authn: ${authn}")          
          concat(            
            get {
              rejectEmptyResponse {
                onSuccess(getAuth(auid)) { rsp =>
                  complete(rsp)
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
