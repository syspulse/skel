package io.syspulse.skel.dash.server

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import java.nio.file.Paths
import scala.annotation.tailrec

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
import scala.concurrent.duration._

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

import io.syspulse.skel.dash._
import io.syspulse.skel.dash.store.DashRegistry
import io.syspulse.skel.dash.store.DashRegistry._
import io.syspulse.skel.dash.server._
import io.syspulse.skel.service.telemetry.TelemetryRegistry

import io.syspulse.skel.util.Util

import io.syspulse.skel.auth.permissions.rbac
import io.syspulse.skel.auth.ext.{ExtAuth,ExtRbacStrict,ExtRbacUser}


// ======================================================================================================================================
@Path("/")
class DashRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable 
  with RouteAuthorizers {
  
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions:Permissions = config.permissions match {
    case "strict" => new ExtRbacStrict(config.adminRole,config.serviceRole,config.rolesAttr) 
    case "user" => new ExtRbacUser(config.adminRole,config.serviceRole,config.rolesAttr)
    case _ => Permissions(config.permissions)
  }

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import DashJson._
    
  //def getModels(pid:Option[String]): Future[Try[ModelsRes]] = registry.ask(Models(pid, _))
  def dash(id:String,tid:Option[String],pid:Option[String]): Future[Try[DashLayout]] = registry.ask(AskDash(id,tid,pid, _))
  def dashes(tid:Option[String],pid:Option[String]): Future[Try[Dashs]] = registry.ask(AskDashs(tid,pid, _))
  def dashCreate(tid:Option[String],pid:Option[String],req:DashCreateReq): Future[Try[DashRes]] = registry.ask(CreateDash(tid,pid, req, _))
  def dashUpdate(id:String,tid:Option[String],pid:Option[String],req:DashUpdateReq): Future[Try[DashRes]] = registry.ask(UpdateDash(id,tid,pid, req, _))
  def dashDelete(id:String,tid:Option[String],pid:Option[String]): Future[Try[DashRes]] = registry.ask(DeleteDash(id,tid,pid, _))
  
  def dashData(id:String,tid:Option[String],pid:Option[String],req:DashDataReq): Future[Try[DashData]] = {    
    registry.ask(AskData(id,tid,pid,req, _))
  }
  
  @GET @Path("/dash/{tid}/{pid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("dash"), summary = "Return all Dashes",
    parameters = Array(
      new Parameter(name = "tid", in = ParameterIn.PATH, description = "Tenant ID"),
      new Parameter(name = "pid", in = ParameterIn.PATH, description = "Project ID"),      
    ), 
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Dashes",content = Array(new Content(schema = new Schema(implementation = classOf[Dashs])))))
  )
  def routeDashes(tid:Option[String],pid:Option[String]) = get {
    complete(dashes(tid,pid))
  }

  @GET @Path("/dash/{tid}/{pid}/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("dash"), summary = "Return Dash",
    parameters = Array(
      new Parameter(name = "tid", in = ParameterIn.PATH, description = "Tenant ID"),
      new Parameter(name = "pid", in = ParameterIn.PATH, description = "Project ID"),
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Dash ID")
    ), 
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Dash",content = Array(new Content(schema = new Schema(implementation = classOf[Dash])))))
  )
  def routeDash(tid:Option[String],pid:Option[String],id:String) = get {
    complete(dash(id,tid,pid))
  }

  @POST @Path("/dash/{tid}/{pid}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("dash"),summary = "Create Dash",
    parameters = Array(
      new Parameter(name = "tid", in = ParameterIn.PATH, description = "Tenant ID"),
      new Parameter(name = "pid", in = ParameterIn.PATH, description = "Project ID"),
    ),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[DashCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Create Dash and return Dash ID",content = Array(new Content(schema = new Schema(implementation = classOf[DashRes])))))
  )
  def routeDashCreate(tid:Option[String],pid:Option[String]) = post {
    entity(as[DashCreateReq]) { req =>
      onSuccess(dashCreate(tid,pid,req)) { r =>
        complete(r)
      }
    }
  }
  
  @PUT @Path("/dash/{tid}/{pid}/${id}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("dash"),summary = "Update dash",
    parameters = Array(
      new Parameter(name = "tid", in = ParameterIn.PATH, description = "Tenant ID"),
      new Parameter(name = "pid", in = ParameterIn.PATH, description = "Project ID"),
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Dash ID")
    ),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[DashUpdateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Dash Update and return updated Dash ID",content = Array(new Content(schema = new Schema(implementation = classOf[DashRes])))))
  )
  def routeDashUpdate(tid:Option[String],pid:Option[String],id:String) = put {
    entity(as[DashUpdateReq]) { req =>
      onSuccess(dashUpdate(id,tid,pid,req.copy(id = Some(id)))) { r =>        
        complete(r)
      }
    }
  }

  @DELETE @Path("/dash/{tid}/{pid}/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("dash"), summary = "Delete Dash",
    parameters = Array(
      new Parameter(name = "tid", in = ParameterIn.PATH, description = "Tenant ID"),
      new Parameter(name = "pid", in = ParameterIn.PATH, description = "Project ID"),
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Dash ID")
    ), 
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Delete Dash and return deleted Dash ID",content = Array(new Content(schema = new Schema(implementation = classOf[DashRes])))))
  )
  def routeDashDelete(tid:Option[String],pid:Option[String],id:String) = delete {
    complete(dashDelete(id,tid,pid))
  }

  @POST @Path("/dash/{tid}/{pid}/{id}/data") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("dash"),summary = "Get Datasource Data",
    parameters = Array(
      new Parameter(name = "tid", in = ParameterIn.PATH, description = "Tenant ID"),
      new Parameter(name = "pid", in = ParameterIn.PATH, description = "Project ID"),
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Dash ID")
    ),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[DashDataReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Request Data from Datasource by Datasource ID. Data returned depends on Datasource src and type",content = Array(new Content(schema = new Schema(implementation = classOf[DashData])))))
  )
  def routeDashData(tid:Option[String],pid:Option[String],id:String) = post {
    entity(as[DashDataReq]) { req => {
      val f = dashData(id,tid,pid,req)
      onSuccess(f) { r =>
        complete(r)
      }
    }}
    
  }

  @GET @Path("/dash/{tid}/{pid}/{id}/data") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("dash"),summary = "Get Datasource Data",
    parameters = Array(
      new Parameter(name = "tid", in = ParameterIn.PATH, description = "Tenant ID"),
      new Parameter(name = "pid", in = ParameterIn.PATH, description = "Project ID"),
      new Parameter(name = "id", in = ParameterIn.PATH, description = "Dash ID")
    ),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[DashDataReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "Request Data from Datasource by Datasource ID. Data returned depends on Datasource src and type",content = Array(new Content(schema = new Schema(implementation = classOf[DashData])))))
  )
  def routeDashDataAsGet(tid:Option[String],pid:Option[String],id:String) = get {
    entity(as[DashDataReq]) { req => {
      val f = dashData(id,tid,pid,req)
      onSuccess(f) { r =>
        complete(r)
      }
    }}
    
  }
  
// =======================================================================================================================================================
  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    concat(
      pathPrefix(Segment) { tid =>
        pathPrefix(Segment) { pid =>          
          pathPrefix(Segment) { id =>
            pathPrefix("data") {
              pathEndOrSingleSlash {
                authenticate()(authn => {
                  authorize(Permissions.isAdmin(authn) || Permissions.isService(authn) || ExtAuth.getOwner(authn) == Some(tid)) {
                    // DashData also validates tenantId
                    // If JWT is Admin, it must pass None in tid
                    val tidRequester = if(permissions.isAdmin(authn) || permissions.isService(authn))
                      None
                    else
                      Some(tid)

                    routeDashData(tidRequester,Some(pid),id) ~
                    routeDashDataAsGet(tidRequester,Some(pid),id)
                  }
                })
              }
            } ~
            pathEndOrSingleSlash {            
              authenticate()(authn => {                

                authorize(Permissions.isAdmin(authn) || Permissions.isService(authn) || ExtAuth.getOwner(authn) == Some(tid)) {
                  
                  val tidRequester = if(permissions.isAdmin(authn) || permissions.isService(authn))
                      None
                    else
                      Some(tid)

                  routeDash(tidRequester,Some(pid),id) ~
                  routeDashUpdate(Some(tid),Some(pid),id) ~
                  routeDashDelete(Some(tid),Some(pid),id)
                }
              })
            }
          } ~
          pathEndOrSingleSlash {
            authenticate()(authn => {              
              authorize(Permissions.isAdmin(authn) || Permissions.isService(authn) || ExtAuth.getOwner(authn) == Some(tid)) {

                val tidRequester = if(permissions.isAdmin(authn) || permissions.isService(authn))
                    None
                  else
                    Some(tid)
                
                // creation must be always under tid context !
                routeDashes(tidRequester,Some(pid)) ~
                routeDashCreate(Some(tid),Some(pid))
              }
            })
          }
        } ~ 
        // get all Dashes for Tenant
        pathEndOrSingleSlash {
          authenticate()(authn => {              
            authorize(Permissions.isAdmin(authn) || Permissions.isService(authn) || ExtAuth.getOwner(authn) == Some(tid)) {
              val tidRequester = if(permissions.isAdmin(authn) || permissions.isService(authn))
                  None
                else
                  Some(tid)
              
              routeDashes(tidRequester,None)
            }
          })
        }
      }
    )
  }
}
