package io.syspulse.ai.server

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

import io.syspulse.skel.auth._
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.RouteAuthorizers

import io.syspulse.ai._
import io.syspulse.ai.store.AiRegistry
import io.syspulse.ai.store.AiRegistry._
import io.syspulse.ai.server._
import io.syspulse.skel.service.telemetry.TelemetryRegistry
import scala.annotation.tailrec
import io.syspulse.skel.auth.permissions.rbac
import io.syspulse.skel.util.Util

@Path("/")
class AiRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_],config:Config) extends CommonRoutes with Routeable 
  with RouteAuthorizers {
  
  implicit val system: ActorSystem[_] = context.system
  
  implicit val permissions = Permissions()

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import AiJson._
  
  val metricGetCount: Counter = Counter.build().name("ai_get_total").help("ai gets").register(TelemetryRegistry.registry)
  val metricCreateCount: Counter = Counter.build().name("ai_create_total").help("ai creats and random creates").register(TelemetryRegistry.registry)
  val metricDeleteCount: Counter = Counter.build().name("ai_delete_total").help("ai deletes").register(TelemetryRegistry.registry)
  val metricSignCount: Counter = Counter.build().name("ai_sign_total").help("ai signs").register(TelemetryRegistry.registry)
  val metricTxCount: Counter = Counter.build().name("ai_tx_total").help("ai transactions").register(TelemetryRegistry.registry)
  val metricBalanceCount: Counter = Counter.build().name("ai_balance_total").help("ai balances").register(TelemetryRegistry.registry)
        
  def getAis(oid:Option[String]): Future[Ais] = registry.ask(GetAis(oid, _))
  def getAi(addr: String,oid:Option[String]): Future[Try[Ai]] = registry.ask(GetAi(addr, oid, _))
  
  def createAi(oid:Option[String],req: AiCreateReq): Future[Try[Ai]] = registry.ask(CreateAi(oid,req, _))
  def deleteAi(addr: String,oid:Option[String]): Future[Try[Ai]] = registry.ask(DeleteAi(addr,oid, _))    

  // @GET @Path("/ai/{oid}/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  // @Operation(tags = Array("Ai"),summary = "Return AML info by Address",
  //   parameters = Array(
  //     new Parameter(name = "oid", in = ParameterIn.PATH, description = "AML Provider id"),
  //     new Parameter(name = "addr", in = ParameterIn.PATH, description = "Address")),
  //   responses = Array(new ApiResponse(responseCode="200",description = "AML returned",content=Array(new Content(schema=new Schema(implementation = classOf[Ai])))))
  // )
  // def getAiRoute(addr: String, oid:Option[String]) = get {
  //   rejectEmptyResponse {
  //     parameters("meta".as[String].optional) { (meta) => 
  //       onSuccess(getAiMeta(addr,meta,oid)) { r =>
  //         metricGetCount.inc()
  //         complete(r)
  //       }
  //     }
  //   }
  // }

  // @GET @Path("/ai/{oid}") @Produces(Array(MediaType.APPLICATION_JSON))
  // @Operation(tags = Array("Ai"), summary = "Return all AML for oid",
  // parameters = Array(
  //     new Parameter(name = "oid", in = ParameterIn.PATH, description = "AML Provider id")),    
  //   responses = Array(
  //     new ApiResponse(responseCode = "200", description = "List of AMLs",content = Array(new Content(schema = new Schema(implementation = classOf[Ais])))))
  // )
  // def getAisRoute(oid:Option[String]) = get {
  //   metricGetCount.inc()
  //   complete(getAis(oid))
  // }

  // @DELETE @Path("/ai/{oid}/{addr}") @Produces(Array(MediaType.APPLICATION_JSON))
  // @Operation(tags = Array("Ai"),summary = "Delete AML by addr for provide oid",
  //   parameters = Array(
  //     new Parameter(name = "oid", in = ParameterIn.PATH, description = "provider id"),
  //     new Parameter(name = "addr", in = ParameterIn.PATH, description = "Address")),
  //   responses = Array(
  //     new ApiResponse(responseCode = "200", description = "AML deleted",content = Array(new Content(schema = new Schema(implementation = classOf[Ai])))))
  // )
  // def deleteAiRoute(addr: String,oid:Option[String]) = delete {
  //   onSuccess(deleteAi(addr,oid)) { r =>
  //     metricDeleteCount.inc()      
  //     complete(StatusCodes.OK, r)
  //   }
  // }

  @POST @Path("/ai/{oid}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("Ai"),summary = "Create Ai",
    parameters = Array(new Parameter(name = "oid", in = ParameterIn.PATH, description = "Provider id")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[AiCreateReq])))),
    responses = Array(new ApiResponse(responseCode = "200", description = "AML",content = Array(new Content(schema = new Schema(implementation = classOf[Ai])))))
  )
  def createAiRoute(oid:Option[String]) = post {
    entity(as[AiCreateReq]) { req =>
      onSuccess(createAi(oid,req)) { r =>
        metricCreateCount.inc()
        complete(StatusCodes.Created, r)
      }
    }
  }

  
// =======================================================================================================================================================
  val corsAllow = CorsSettings(system.classicSystem)
    .withAllowCredentials(true)
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))

  override def routes: Route = cors(corsAllow) {
    concat(
      pathEndOrSingleSlash {
        concat(
          authenticate()(authn => 
            authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
              // getAisRoute(None) ~
              createAiRoute(None)                
            // } 
            // else {
            //   getAisRoute(authn.getUser)
            // }
          })   
        )
      },
            
      pathPrefix(Segment) { oid => concat(
        // pathPrefix(Segment) { addr =>             
        //   pathEndOrSingleSlash {            
        //     authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
        //       getAiRoute(addr,Some(oid)) ~
        //       deleteAiRoute(addr,Some(oid))
        //     }) 
        //   }
        // },
        pathEndOrSingleSlash {            
          authenticate()(authn => authorize(Permissions.isAdmin(authn) || Permissions.isService(authn)) {
            createAiRoute(Some(oid))
            //~ getAisRoute(Some(oid))   
          })
        }
      )}
      
    )      
  }
}
