package io.syspulse.skeleton

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

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import javax.ws.rs.core.MediaType



import io.syspulse.skeleton.TelemetryRegistry._

@Path("/api/v1/telemetry")
class TelemetryRoutes(telemetryRegistry: ActorRef[TelemetryRegistry.Command])(implicit val system: ActorSystem[_]) {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import TelemetryJson._
  
  private implicit val timeout = Timeout.create(
    system.settings.config.getDuration("telemetry.routes.ask-timeout")
  )

  
  def getTelemetries(): Future[Telemetries] = telemetryRegistry.ask(GetTelemetries)
  def getTelemetry(key: String): Future[GetTelemetryResponse] = telemetryRegistry.ask(GetTelemetry(key, _))

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"),summary = "Return Telemetry by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "telemetry-id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Telemetry returned",content=Array(new Content(schema=new Schema(implementation = classOf[Telemetry])))))
  )
  def getTelemetryRoute(key: String) = get {
    rejectEmptyResponse {
      onSuccess(getTelemetry(key)) { response =>
        complete(response.telemetry)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"), summary = "Return all Telemetry",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Telemetry",content = Array(new Content(schema = new Schema(implementation = classOf[Telemetries])))))
  )
  def getTelemetriesRoute() = get {
    complete(getTelemetries())
  }


  val routes: Route =
    pathPrefix("telemetry") {
      concat(
        pathEndOrSingleSlash {
          concat(
            getTelemetriesRoute()
          )
        },
        path(Segment) { key =>
          concat(
            getTelemetryRoute(key)
          )
        }
      )
    }

}
