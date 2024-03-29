package io.syspulse.skel.otp.server

import scala.util.Try

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.`Content-Type`
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.model.StatusCodes._
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.auth.RouteAuthorizers
//import io.syspulse.skel.service.swagger.SwaggerLike

import io.syspulse.skel.Command

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import io.syspulse.skel.otp._
import io.syspulse.skel.otp.store.OtpRegistry._
import io.syspulse.skel.auth.permissions.Permissions

@Path("/")
class OtpRoutes(otpRegistry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable with RouteAuthorizers {
  override val log = Logger(s"${this}")  
  implicit val system: ActorSystem[_] = context.system

  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._
  
  // registry is needed because Unit-tests with multiple Routes in Suites will fail (Prometheus libary quirk)
  val cr = new CollectorRegistry(true);
  val metricGetCount: Counter = Counter.build().name("skel_otp_get_total").help("OTP gets").register(cr)
  val metricDeleteCount: Counter = Counter.build().name("skel_otp_delete_total").help("OTP deletes").register(cr)
  val metricCreateCount: Counter = Counter.build().name("skel_otp_create_total").help("OTP creates").register(cr)
  val metricGetCodeCount: Counter = Counter.build().name("skel_otp_code_total").help("OTP code").register(cr)
  val metricVerifyCodeCount: Counter = Counter.build().name("skel_otp_verify_total").help("OTP verify").register(cr)
  val metricGetRandomCount: Counter = Counter.build().name("skel_otp_random_total").help("OTP random").register(cr)

  def getOtps(): Future[Otps] = otpRegistry.ask(GetOtps)
  def getUserOtps(userId:UUID): Future[Otps] = otpRegistry.ask(GetUserOtps(userId,_))
  def getOtp(id: UUID): Future[Try[Otp]] = otpRegistry.ask(GetOtp(id, _))

  def getOtpCode(id: UUID): Future[Try[OtpCode]] = otpRegistry.ask(GetOtpCode(id, _))
  def getOtpCodeVerify(id: UUID, code:String): Future[OtpCodeVerifyRes] = otpRegistry.ask(GetOtpCodeVerify(id,code, _))

  def createOtp(otpCreate: OtpCreateReq): Future[OtpCreateRes] = otpRegistry.ask(CreateOtp(otpCreate, _))
  def deleteOtp(id: UUID): Future[OtpActionRes] = otpRegistry.ask(DeleteOtp(id, _))
  def randomOtp(otpRandom: OtpRandomReq): Future[OtpRandomRes] = otpRegistry.ask(RandomOtp(otpRandom, _))
  def randomHtml(otpRandom: OtpRandomReq): Future[String] = otpRegistry.ask(RandomHtml(otpRandom, _))

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Return OTP by id",
    method = "GET",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "OTP returned",content=Array(new Content(schema=new Schema(implementation = classOf[Otp])))))
  )
  def getOtpRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getOtp(UUID.fromString(id))) { r =>
        metricGetCount.inc()
        complete(r)
      }
    }
  }


  @GET @Path("/{id}/code") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Get OTP code by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "OTP Code returned",content=Array(new Content(schema=new Schema(implementation = classOf[OtpCode])))))
  )
  def getOtpCodeRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getOtpCode(UUID.fromString(id))) { r =>
        metricGetCodeCount.inc()
        complete(r)
      }
    }
  }


  @GET @Path("/{id}/code/{code}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Verify OTP code by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "OTP Code returned",content=Array(new Content(schema=new Schema(implementation = classOf[OtpCode])))))
  )
  def getOtpCodeVerifyRoute(id: String,code:String) = get {
    rejectEmptyResponse {
      onSuccess(getOtpCodeVerify(UUID.fromString(id),code)) { r =>
        metricVerifyCodeCount.inc()
        complete(r)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"), summary = "Return all OTPs",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of OTPs",content = Array(new Content(schema = new Schema(implementation = classOf[Otps])))))
  )
  def getOtpsRoute() = get {
    metricGetCount.inc()
    complete(getOtps())
  }

  @GET @Path("/user") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"), summary = "Return all OTPs for User",
    parameters = Array(new Parameter(name = "userId", in = ParameterIn.PATH, description = "User id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of OTPs for User",content = Array(new Content(schema = new Schema(implementation = classOf[Otps])))))
  )
  def getUserOtpsRoute(userId:String) = get {
    metricGetCount.inc()
    complete(getUserOtps(UUID.fromString(userId)))
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Delete OTP by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "OTP deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Otp])))))
  )
  def deleteOtpRoute(id: String) = delete {
    onSuccess(deleteOtp(UUID.fromString(id))) { r =>
      metricDeleteCount.inc()
      complete((StatusCodes.OK, r))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Create OTP Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OtpCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "OTP created",content = Array(new Content(schema = new Schema(implementation = classOf[OtpActionRes])))))
  )
  def createOtpRoute = post {
    entity(as[OtpCreateReq]) { otpCreate =>
      onSuccess(createOtp(otpCreate)) { r =>
        metricCreateCount.inc()
        complete((StatusCodes.Created, r))
      }
    }
  }

  @GET @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON,MediaType.TEXT_HTML))
  @Operation(tags = Array("otp"), summary = "Generate Random OTP secret, QR code (and HTML temlate for testing)",
    parameters = Array(new Parameter(name = "name", in = ParameterIn.PATH, description = "OTP name"),
                       new Parameter(name = "account", in = ParameterIn.PATH, description = "OTP account"),
                       new Parameter(name = "issuer", in = ParameterIn.PATH, description = "OTP issuer"),
                       new Parameter(name = "format", in = ParameterIn.PATH, description = "'html' - generates test HTML with QR code image")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OtpRandomReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Randomg OTP secret",content = Array(new Content(schema = new Schema(implementation = classOf[OtpRandomRes])))))
  )
  def getOtpRandomRoute() = get { parameters("format".optional,"name".optional,"account".optional,"issuer".optional) { (format,name,account,issuer) => 
      metricGetRandomCount.inc()

      entity(as[OtpRandomReq]) { otpRandom =>
        val r = randomOtp(otpRandom)
        complete(r)
      } ~ { 
        // this is needed for "empty" Body for default (Option[OtpRandom] does not work!)
        format.getOrElse("").toLowerCase match {
          case "html" => {
            implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
            val htmlOut = randomHtml(OtpRandomReq(name,account,issuer))
            // NOTE: complete(200,List(`Content-Type`(`text/html(UTF-8)`)),h) is not working because 
            val h = Await.result(htmlOut,Duration.Inf)
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, h))

            //
          }
          case _ =>  complete(randomOtp(OtpRandomReq(name,account,issuer)))    
        }
      }
    }
  }

  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
      concat(
        pathEndOrSingleSlash {
          concat(
            authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getOtpsRoute() ~
                createOtpRoute
              }
            )
          )
        },
        pathPrefix("user") {
          path(Segment) { uid =>
            authenticate()(authn =>  authorize(Permissions.isUser(UUID(uid),authn) || Permissions.isAdmin(authn) || Permissions.isService(authn)) { 
                getUserOtpsRoute(uid)
              }
            )
          }
        },
        pathSuffix("random") {
          getOtpRandomRoute()
        },
        pathPrefix(Segment) { id => 
          authenticate()(authn =>
            pathPrefix("code") {
              pathEndOrSingleSlash {
                getOtpCodeRoute(id)
              } ~
              path(Segment) { code =>
                getOtpCodeVerifyRoute(id,code)
              }
            } ~
            pathEndOrSingleSlash {
              concat(
                getOtpRoute(id),
                deleteOtpRoute(id),
              )
            }
          ) 
        }
      )
  }
    
}
