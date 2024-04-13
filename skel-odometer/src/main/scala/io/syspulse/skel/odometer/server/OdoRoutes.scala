package io.syspulse.skel.odometer.server

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import java.nio.file.Paths

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.FileIO

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

import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.skel.odometer._
import io.syspulse.skel.odometer.store.OdoRegistry
import io.syspulse.skel.odometer.store.OdoRegistry._
import io.syspulse.skel.odometer.server.{Odos, OdoRes, OdoCreateReq, OdoUpdateReq}
import io.syspulse.skel.service.telemetry.TelemetryRegistry
import io.syspulse.skel.service.ws.WebSocket
import scala.concurrent.ExecutionContext

@Path("/")
class OdoRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config,ex:ExecutionContext) 
  extends WebSocket(config.timeoutIdle)(ex) with CommonRoutes with Routeable with RouteAuthorizers {
  
  override val log = Logger(s"${this}")

  implicit val system: ActorSystem[_] = context.system  
  
  implicit val permissions = Permissions()

  import io.syspulse.skel.odometer.store.OdoRegistryProto._

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import spray.json._
  import OdoJson._
    
  def getOdos(): Future[Odos] = registry.ask(GetOdos)
  def getOdo(id: String): Future[Try[Odos]] = registry.ask(GetOdo(id, _))
  
  def createOdo(req: OdoCreateReq): Future[Try[Odos]] = registry.ask(CreateOdo(req, _))
  def updateOdo(id:String,req: OdoUpdateReq): Future[Try[Odos]] = registry.ask(UpdateOdo(id,req, _))
  def deleteOdo(id: String): Future[Try[String]] = registry.ask(DeleteOdo(id, _))
  

  @GET @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("odometer"),summary = "Return Odo by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Odo id (uuid)")),
    responses = Array(new ApiResponse(responseCode="200",description = "Odo returned",content=Array(new Content(schema=new Schema(implementation = classOf[Odo])))))
  )
  def getOdoRoute(id: String) = get {
    rejectEmptyResponse {
      onSuccess(getOdo(id)) { r =>
        complete(r)
      }
    }
  }

  
  @GET @Path("/") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("odometer"), summary = "Return all Odos",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Odos",content = Array(new Content(schema = new Schema(implementation = classOf[Odos])))))
  )
  def getOdosRoute() = get {
    complete(getOdos())
  }

  @DELETE @Path("/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("odometer"),summary = "Delete Odo by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Odo id (uuid)")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Odo deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Odo])))))
  )
  def deleteOdoRoute(id: String) = delete {
    onSuccess(deleteOdo(id)) { r =>
      complete(StatusCodes.OK, r)
    }
  }

  @POST @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("odometer"),summary = "Create Odo",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OdoCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Odo",content = Array(new Content(schema = new Schema(implementation = classOf[Odo])))))
  )
  def createOdoRoute = post {
    entity(as[OdoCreateReq]) { req =>
      onSuccess(createOdo(req)) { r =>
        // update subscribers
        if(r.isSuccess) broadcastText(r.get.toJson.compactPrint)
        complete(StatusCodes.Created, r)
      }
    }
  }

  @PUT @Path("/") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("odometer"),summary = "Update Odo",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[OdoUpdateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Odo",content = Array(new Content(schema = new Schema(implementation = classOf[Odo])))))
  )
  def updateOdoRoute(id:String) = put {
    entity(as[OdoUpdateReq]) { req =>
      onSuccess(updateOdo(id,req)) { r =>
        if(r.isSuccess) broadcastText(r.get.toJson.compactPrint)
        complete(StatusCodes.OK, r)
      }
    }
  }

    
  val corsAllow = CorsSettings(system.classicSystem)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
      concat(
        pathEndOrSingleSlash {
          concat(
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                getOdosRoute() ~                
                createOdoRoute  
              }
            ),            
          )
        },
        pathPrefix("ws") { 
          extractClientIP { addr => {
            log.info(s"<-- ws://${addr}")
            
            pathPrefix(Segment) { group =>
              handleWebSocketMessages(this.listen(group))
            } ~
            pathEndOrSingleSlash {
              handleWebSocketMessages(this.listen())
            }
          }}
        },
        pathPrefix(Segment) { id => 
          pathEndOrSingleSlash {
            authenticate()(authn =>
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                updateOdoRoute(id) ~
                getOdoRoute(id)                 
              } ~
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
                deleteOdoRoute(id)
              }
            ) 
          }
        },        
      )
  }
}
