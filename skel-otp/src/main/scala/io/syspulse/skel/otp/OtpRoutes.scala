package io.syspulse.skel.otp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
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
import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import javax.ws.rs.core.MediaType

import nl.grons.metrics4.scala.DefaultInstrumented
import nl.grons.metrics4.scala.Counter
import nl.grons.metrics4.scala.MetricName

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.otp.OtpRegistry._
import scala.concurrent.Await
import scala.concurrent.duration.Duration

@Path("/api/v1/otp")
class OtpRoutes(otpRegistry: ActorRef[OtpRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("otp.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("otp-get-count")
  val metricPostCount: Counter = metrics.counter("otp-post-count")
  val metricDeleteCount: Counter = metrics.counter("otp-delete-count")
  val metricGetCodeCount: Counter = metrics.counter("otp-get-code-count")
  val metricVerifyCodeCount: Counter = metrics.counter("otp-verify-code-count")
  val metricGetRandomCount: Counter = metrics.counter("otp-get-random-count")

  def getOtps(): Future[Otps] = otpRegistry.ask(GetOtps)
  def getUserOtps(userId:UUID): Future[Otps] = otpRegistry.ask(GetUserOtps(userId,_))
  def getOtp(id: UUID): Future[GetOtpResponse] = otpRegistry.ask(GetOtp(id, _))

  def getOtpCode(id: UUID): Future[GetOtpCodeResponse] = otpRegistry.ask(GetOtpCode(id, _))
  def getOtpCodeVerify(id: UUID, code:String): Future[GetOtpCodeVerifyResponse] = otpRegistry.ask(GetOtpCodeVerify(id,code, _))

  def createOtp(otpCreate: OtpCreate): Future[OtpCreateResult] = otpRegistry.ask(CreateOtp(otpCreate, _))
  def deleteOtp(id: UUID): Future[OtpActionResult] = otpRegistry.ask(DeleteOtp(id, _))
  def randomOtp(otpRandom: OtpRandom): Future[OtpRandomResult] = otpRegistry.ask(RandomOtp(otpRandom, _))
  def randomHtml(otpRandom: OtpRandom): Future[String] = otpRegistry.ask(RandomHtml(otpRandom, _))

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Return OTP by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "OTP returned",content=Array(new Content(schema=new Schema(implementation = classOf[Otp])))))
  )
  def getOtpRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getOtp(UUID.fromString(id))) { response =>
        metricGetCount += 1
        complete(response.otp)
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
      onSuccess(getOtpCode(UUID.fromString(id))) { response =>
        metricGetCodeCount += 1
        complete(response)
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
      onSuccess(getOtpCodeVerify(UUID.fromString(id),code)) { response =>
        metricVerifyCodeCount += 1
        complete(response)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"), summary = "Return all OTPs",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of OTPs",content = Array(new Content(schema = new Schema(implementation = classOf[Otps])))))
  )
  def getOtpsRoute() = get {
    metricGetCount += 1
    complete(getOtps())
  }

  @GET @Path("/user") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"), summary = "Return all OTPs for User",
    parameters = Array(new Parameter(name = "userId", in = ParameterIn.PATH, description = "User id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of OTPs for User",content = Array(new Content(schema = new Schema(implementation = classOf[Otps])))))
  )
  def getUserOtpsRoute(userId:String) = get {
    metricGetCount += 1
    complete(getUserOtps(UUID.fromString(userId)))
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Delete OTP by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "OTP deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Otp])))))
  )
  def deleteOtpRoute(id: String) = delete {
    onSuccess(deleteOtp(UUID.fromString(id))) { result =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, result))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Create OTP Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OtpCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "OTP created",content = Array(new Content(schema = new Schema(implementation = classOf[OtpActionResult])))))
  )
  def createOtpRoute = post {
    entity(as[OtpCreate]) { otpCreate =>
      onSuccess(createOtp(otpCreate)) { result =>
        metricPostCount += 1
        complete((StatusCodes.Created, result))
      }
    }
  }

  @GET @Path("/random") @Produces(Array(MediaType.APPLICATION_JSON,MediaType.TEXT_HTML))
  @Operation(tags = Array("otp"), summary = "Generate Random OTP secret, QR code (and HTML temlate for testing)",
    parameters = Array(new Parameter(name = "name", in = ParameterIn.PATH, description = "OTP name"),
                       new Parameter(name = "account", in = ParameterIn.PATH, description = "OTP account"),
                       new Parameter(name = "issuer", in = ParameterIn.PATH, description = "OTP issuer"),
                       new Parameter(name = "format", in = ParameterIn.PATH, description = "'html' - generates test HTML with QR code image")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OtpRandom])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Randomg OTP secret",content = Array(new Content(schema = new Schema(implementation = classOf[OtpRandom])))))
  )
  def getOtpRandomRoute() = get { parameters("format".optional,"name".optional,"account".optional,"issuer".optional) { (format,name,account,issuer) => 
      metricGetRandomCount += 1

      entity(as[OtpRandom]) { otpRandom =>
        val r = randomOtp(otpRandom)
        complete(r)
      } ~ { 
        // this is needed for "empty" Body for default (Option[OtpRandom] does not work!)
        format.getOrElse("").toLowerCase match {
          case "html" => {
            implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
            val htmlOut = randomHtml(OtpRandom(name,account,issuer))
            val h = Await.result(htmlOut,Duration.Inf)
            //complete(200,List(`Content-Type`(`text/html(UTF-8)`)),h)
            complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, h))
          }
          case _ =>  complete(randomOtp(OtpRandom(name,account,issuer)))    
        }
      }
    }
  }

  override val routes: Route =
      concat(
        pathEndOrSingleSlash {
          concat(
            getOtpsRoute(),
            createOtpRoute
          )
        },
        pathPrefix("user") {
          path(Segment) { userId => 
            getUserOtpsRoute(userId)
          }
        },
        pathSuffix("random") {
          getOtpRandomRoute()
        },
        pathPrefix(Segment) { id => 
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
        }
      )
    
}
