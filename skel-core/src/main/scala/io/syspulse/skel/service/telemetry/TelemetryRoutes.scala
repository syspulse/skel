package io.syspulse.skel.service.telemetry

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
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType


import fr.davit.akka.http.metrics.core._
import fr.davit.akka.http.metrics.prometheus.marshalling.PrometheusMarshallers._
import fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.metrics
import fr.davit.akka.http.metrics.core.{HttpMetricsRegistry, HttpMetricsSettings}
import fr.davit.akka.http.metrics.core.HttpMetrics._

import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.service.telemetry.TelemetryRegistry._
import akka.actor.typed.scaladsl.ActorContext

@Path("/telemetry")
class TelemetryRoutes(telemetryRegistry: ActorRef[TelemetryRegistry.Command])(implicit context: ActorContext[_]) extends CommonRoutes {
  
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import TelemetryRegistry._
  
  // this is dead code needed only for Swagger
  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("telemetry"), summary = "Return all Prometheus Telemetry",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Telemetry",content = Array(new Content(schema = new Schema()))))
  )
  def getTelemetriesRoute() = {}

  val routes: Route =
    pathPrefix("telemetry") {
      fr.davit.akka.http.metrics.core.scaladsl.server.HttpMetricsDirectives.metrics(prometheusRegistry)
    }
}



