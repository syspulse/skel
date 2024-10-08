package io.syspulse.skel.service.config

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

import io.jvm.uuid._

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
//import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
//import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import io.prometheus.client.Counter

import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.service.config.ConfigRegistry._
import akka.actor.typed.scaladsl.ActorContext


@Path("/config")
class ConfigRoutes(configRegistry: ActorRef[ConfigRegistry.Command])(implicit context: ActorContext[_]) extends CommonRoutes {
  implicit val system: ActorSystem[_] = context.system
  
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import ConfigJson._
  
  //val metricGetAllCount = Counter.build().name("skel_config_requests_total").help("Total Configuration requests.").register()
  
  def getConfigAll(): Future[Configs] = configRegistry.ask(GetConfigAll( _))

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("config"), summary = "Return Config",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "configuration",content = Array(new Content(schema = new Schema(implementation = classOf[Configs])))))
  )
  def getConfigAllRoute() = get {
    //metricGetAllCount.inc()
    complete(getConfigAll())
  }

  val routes: Route =
    pathPrefix("config") {
      concat(
        pathEndOrSingleSlash {
          concat(
            getConfigAllRoute()
          )
        }
      )
    }
}
