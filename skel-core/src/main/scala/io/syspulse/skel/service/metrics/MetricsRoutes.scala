package io.syspulse.skel.service.metrics

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


import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.service.metrics.MetricsRegistry._
import akka.actor.typed.scaladsl.ActorContext

@Path("/api/v1/{service}/metrics")
class MetricsRoutes(metricsRegistry: ActorRef[MetricsRegistry.Command])(implicit context: ActorContext[_]) extends CommonRoutes {
  implicit val system: ActorSystem[_] = context.system

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import MetricsJson._
    
  def getTelemetries(): Future[Telemetries] = metricsRegistry.ask(GetTelemetries)
  def getMetrics(key: String): Future[GetMetricsResponse] = metricsRegistry.ask(GetMetrics(key, _))

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("metrics"),summary = "Return Metrics by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "metrics-id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Metrics returned",content=Array(new Content(schema=new Schema(implementation = classOf[Metrics])))))
  )
  def getMetricsRoute(key: String) = get {
    rejectEmptyResponse {
      onSuccess(getMetrics(key)) { response =>
        complete(response.metrics)
      }
    }
  }

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("metrics"), summary = "Return all Metrics",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Metrics",content = Array(new Content(schema = new Schema(implementation = classOf[Telemetries])))))
  )
  def getTelemetriesRoute() = get {
    complete(getTelemetries())
  }


  val routes: Route =
    pathPrefix("metrics") {
      concat(
        pathEndOrSingleSlash {
          concat(
            getTelemetriesRoute()
          )
        },
        path(Segment) { key =>
          concat(
            getMetricsRoute(key)
          )
        }
      )
    }

}
