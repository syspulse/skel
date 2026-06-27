package io.syspulse.skel.telemetry.ext.server

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import java.nio.file.Paths
import scala.annotation.tailrec

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO
import akka.http.scaladsl.coding.Coders
import akka.http.scaladsl.model.headers
import akka.http.scaladsl.model.{MediaTypes, HttpEntity}

import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration

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

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody
// import javax.ws.rs.{Consumes, POST, GET, DELETE, Path, Produces}
// import javax.ws.rs.core.MediaType
import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType


import io.prometheus.client.CollectorRegistry
import io.prometheus.client.Counter

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes

import io.syspulse.skel.Command

import io.syspulse.skel.auth._
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.telemetry.ext.{TelemetryChain,TelemetryExt}
import io.syspulse.skel.telemetry.ext.store.TelemetryExtRegistry.{ GetTelemetry, SaveTelemetry } 
import io.syspulse.skel.telemetry.ext.store.TelemetryExtRegistry
import io.syspulse.skel.telemetry.ext.TelemetryExtJson

import io.syspulse.skel.auth.permissions.rbac
import io.syspulse.skel.util.Util

import io.syspulse.skel.auth.permissions.rbac
import io.syspulse.skel.auth.ext.{ExtAuth,ExtRbacStrict,ExtRbacUser}

import io.syspulse.skel.telemetry.ext.Config

// ======================================================================================================================================
@Path("/")
class TelemetryExtRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable 
  with RouteAuthorizers {
  
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions:Permissions = config.permissions match {
    case "strict" => new ExtRbacStrict(config.adminRole,config.serviceRole,config.rolesAttr) 
    case "user" => new ExtRbacUser(config.adminRole,config.serviceRole,config.rolesAttr)
    case _ => Permissions(config.permissions)
  }

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import TelemetryExtJson._
  
  def getTelemetry(key:Option[String],oid:Option[String]): Future[Try[TelemetryChain]] = registry.ask(GetTelemetry(key, oid, _))
      
  @GET @Path("/telemetry/ext/{oid}/{key}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("TelemetryExt"),summary = "Return TelemetryExt info by Key",
    parameters = Array(
      new Parameter(name = "oid", in = ParameterIn.PATH, description = "Owner id"),
      new Parameter(name = "key", in = ParameterIn.PATH, description = "Key")),
    responses = Array(new ApiResponse(
      responseCode="200",
      description = "TelemetryExt returned",
      content=Array(new Content(schema=new Schema(implementation = classOf[TelemetryChainRes])))))
  )
  def getTelemetryRoute(key: Option[String], oid:Option[String]) = get {
    rejectEmptyResponse {
      parameters("meta".as[String].optional) { (meta) => 
        onSuccess(getTelemetry(key,oid)) { r =>
          complete(r)
        }
      }
    }
  }
  
  
// =======================================================================================================================================================
  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))
  

  override def routes: Route = cors(corsAllow) {
    concat(
      pathEndOrSingleSlash {
        concat(
          authenticate()(authn => 
            authorize(permissions.isAdmin(authn) || permissions.isService(authn)) {
              getTelemetryRoute(None,None)
            }
          )   
        )
      },      
    )      
  }
}
