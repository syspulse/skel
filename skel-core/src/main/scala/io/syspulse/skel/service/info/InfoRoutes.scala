package io.syspulse.skel.service.info

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

//import java.util.UUID
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


import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.service.info.InfoRegistry._
import akka.actor.typed.scaladsl.ActorContext

@Path("/info")
class InfoRoutes(infoRegistry: ActorRef[InfoRegistry.Command])(implicit context: ActorContext[_]) extends CommonRoutes {
  implicit val system: ActorSystem[_] = context.system
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import InfoJson._
  
  def getInfo(): Future[Info] = infoRegistry.ask(GetInfo( _))

  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("info"), summary = "Return Component Info",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "information",content = Array(new Content(schema = new Schema(implementation = classOf[Info])))))
  )
  def getInfoRoute() = get {
    complete(getInfo())
  }


  val routes: Route =
    pathPrefix("info") {
      concat(
        pathEndOrSingleSlash {
          concat(
            getInfoRoute()
          )
        }
      )
    }

}
