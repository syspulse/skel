package io.syspulse.skel.wf.temporal.workflow.server

import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route

import scala.concurrent.Future
import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.AskPattern._
import akka.util.Timeout
import scala.concurrent.duration._

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.headers.`Content-Type`

import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import io.swagger.v3.oas.annotations.parameters.RequestBody

import jakarta.ws.rs.{Consumes, POST, PUT, GET, DELETE, Path, Produces}
import jakarta.ws.rs.core.MediaType

import io.syspulse.skel.service.Routeable
import io.syspulse.skel.service.CommonRoutes
import io.syspulse.skel.Command

import io.syspulse.skel.wf.temporal.workflow.store.WorkflowRegistry
import io.syspulse.skel.wf.temporal.workflow.store.WorkflowRegistry._
import io.hacken.ext.wf.WorkflowSchema

@Path("/")
class WorkflowRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable {

  implicit val system: ActorSystem[_] = context.system

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  
  def getWorkflow(id:Int): Future[Try[WorkflowSchema]] = registry.ask(GetWorkflow(id, _))
  def getWorkflows(): Future[Try[Workflows]] = registry.ask(GetWorkflows(_))
  def createWorkflow(req:WorkflowCreateReq): Future[Try[WorkflowRes]] = registry.ask(CreateWorkflow(req, _))
  def updateWorkflow(id:Int, req:WorkflowUpdateReq): Future[Try[WorkflowRes]] = registry.ask(UpdateWorkflow(id, req, _))
  def deleteWorkflow(id:Int): Future[Try[WorkflowRes]] = registry.ask(DeleteWorkflow(id, _))

  @GET @Path("/schema") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"), summary = "Return all Workflow Schemas",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Workflow Schemas",
        content = Array(new Content(schema = new Schema(implementation = classOf[Workflows]))))
    )
  )
  def getWorkflowsRoute() = get {    
    complete(getWorkflows())    
  }

  @GET @Path("/schema/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"), summary = "Return Workflow Schema by id",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Workflow id")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow Schema",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSchema])))),
      new ApiResponse(responseCode = "404", description = "Workflow not found")
    )
  )
  def getWorkflowRoute() = get {
    path(IntNumber) { (id) =>
      rejectEmptyResponse {
        complete(getWorkflow(id))
      }
    }
  }

  @POST @Path("/schema") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Create Workflow Schema",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowCreateReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow created",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowRes]))))
    )
  )
  def createWorkflowRoute() = post {    
    entity(as[WorkflowCreateReq]) { req =>
      complete(createWorkflow(req))
    }
  }

  @PUT @Path("/schema/{id}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Update Workflow Schema",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Workflow id")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowUpdateReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow updated",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowRes])))),
      new ApiResponse(responseCode = "404", description = "Workflow not found")
    )
  )
  def updateWorkflowRoute() = put {
    path(IntNumber) { (id) =>
      entity(as[WorkflowUpdateReq]) { req =>
        complete(updateWorkflow(id, req))
      }
    }
  }

  @DELETE @Path("/schema/{id}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Delete Workflow Schema",
    parameters = Array(new Parameter(name = "id", in = ParameterIn.PATH, description = "Workflow id")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow deleted",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowRes])))),
      new ApiResponse(responseCode = "404", description = "Workflow not found")
    )
  )
  def deleteWorkflowRoute() = delete {
    path(IntNumber) { (id) =>
      complete(deleteWorkflow(id))
    }
  }

  val corsAllow = CorsSettings(system.classicSystem)
    //.withAllowGenericHttpRequests(true)
    .withAllowCredentials(true)    
    .withAllowedMethods(Seq(HttpMethods.OPTIONS,HttpMethods.GET,HttpMethods.POST,HttpMethods.PUT,HttpMethods.DELETE,HttpMethods.HEAD))
  override def routes: Route = cors(corsAllow) {
    concat(
      pathPrefix("schema") {
        concat(          
          getWorkflowRoute(),
          createWorkflowRoute(),
          updateWorkflowRoute(),
          deleteWorkflowRoute(),
          getWorkflowsRoute(),
        )
      }      
    )
  }
}
