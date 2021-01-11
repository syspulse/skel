package io.syspulse.auth.otp

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout

import akka.http.scaladsl.model._
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

import io.syspulse.skel.Routeable
import io.syspulse.auth.otp.OtpRegistry._

@Path("/api/v1/otp")
class OtpRoutes(otpRegistry: ActorRef[OtpRegistry.Command])(implicit val system: ActorSystem[_]) extends DefaultInstrumented with Routeable {
  val log = Logger(s"${this}")  

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("auth-otp.routes.ask-timeout")
  )

  override lazy val metricBaseName = MetricName("")
  val metricGetCount: Counter = metrics.counter("otp-get-count")
  val metricPostCount: Counter = metrics.counter("otp-post-count")
  val metricDeleteCount: Counter = metrics.counter("otp-delete-count")


  def getOtps(): Future[Otps] = otpRegistry.ask(GetOtps)
  def getOtp(id: UUID): Future[GetOtpResponse] = otpRegistry.ask(GetOtp(id, _))
  def createOtp(otpCreate: OtpCreate): Future[OtpActionPerformed] = otpRegistry.ask(CreateOtp(otpCreate, _))
  def deleteOtp(id: UUID): Future[OtpActionPerformed] = otpRegistry.ask(DeleteOtp(id, _))


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

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"), summary = "Return all OTPs",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of OTPs",content = Array(new Content(schema = new Schema(implementation = classOf[Otps])))))
  )
  def getOtpsRoute() = get {
    metricGetCount += 1
    complete(getOtps())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Delete OTP by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "OTP id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "OTP deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Otp])))))
  )
  def deleteOtpRoute(id: String) = delete {
    onSuccess(deleteOtp(UUID.fromString(id))) { performed =>
      metricDeleteCount += 1
      complete((StatusCodes.OK, performed))
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("otp"),summary = "Create OTP Secret",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OtpCreate])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "OTP created",content = Array(new Content(schema = new Schema(implementation = classOf[OtpActionPerformed])))))
  )
  def createOtpRoute = post {
    entity(as[OtpCreate]) { otpCreate =>
      onSuccess(createOtp(otpCreate)) { performed =>
        metricPostCount += 1
        complete((StatusCodes.Created, performed))
      }
    }
  }

  override val routes: Route =
    pathPrefix("otp") {
      concat(
        pathEndOrSingleSlash {
          concat(
            getOtpsRoute(),
            createOtpRoute
          )
        },
        path(Segment) { id =>
          concat(
            getOtpRoute(id),
            deleteOtpRoute(id)
          )
        }
      )
    }

}
