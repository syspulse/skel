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
import io.syspulse.skel.wf.temporal._

@Path("/")
class WorkflowRoutes(registry: ActorRef[Command])(implicit context: ActorContext[_]) extends CommonRoutes with Routeable {

  implicit val system: ActorSystem[_] = context.system

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.{WorkflowSchemaJson, WorkflowRunJson}
  import WorkflowSchemaJson._
  import WorkflowRunJson._
  import io.syspulse.skel.wf.temporal.TemporalJson._
  
  def getWorkflow(id:Int): Future[Try[WorkflowSchema]] = registry.ask(GetWorkflow(id, _))
  def getWorkflows(): Future[Try[Workflows]] = registry.ask(GetWorkflows(_))
  def createWorkflow(req:WorkflowCreateReq): Future[Try[WorkflowRes]] = registry.ask(CreateWorkflow(req, _))
  def updateWorkflow(id:Int, req:WorkflowUpdateReq): Future[Try[WorkflowRes]] = registry.ask(UpdateWorkflow(id, req, _))
  def deleteWorkflow(id:Int): Future[Try[WorkflowRes]] = registry.ask(DeleteWorkflow(id, _))

  // Temporal workflow management methods
  def temporalQuery(req:TemporalQueryReq): Future[Try[QueryResult]] = registry.ask(TemporalQuery(req, _))
  def temporalList(req:TemporalListReq): Future[Try[QueryResult]] = registry.ask(TemporalList(req, _))
  def temporalDescribe(workflowId:String, runId:Option[String]): Future[Try[WorkflowExecutionInfo]] = registry.ask(TemporalDescribe(workflowId, runId, _))
  def temporalGet(runId:String): Future[Try[WorkflowExecutionInfo]] = registry.ask(TemporalGet(runId, _))

  // Workflow start method
  def workflowStart(req:WorkflowStartReq): Future[Try[WorkflowStartRes]] = registry.ask(WorkflowStart(req, _))

  // Workflow signal method
  def workflowSignal(runId:String, req:WorkflowSignalReq): Future[Try[WorkflowSignalRes]] = registry.ask(WorkflowSignal(runId, req, _))

  // Step input update method
  def updateStepInput(runId:String, req:StepInputReq): Future[Try[StepInputRes]] = registry.ask(UpdateStepInput(runId, req, _))

  // Workflow Run methods
  def getWorkflowRun(rid:String): Future[Try[io.hacken.ext.wf.WorkflowRun]] = registry.ask(GetWorkflowRun(rid, _))
  def getWorkflowRuns(): Future[Try[WorkflowRuns]] = registry.ask(GetWorkflowRuns(_))
  def createWorkflowRun(req:WorkflowRunCreateReq): Future[Try[WorkflowRunCreateRes]] = registry.ask(CreateWorkflowRun(req, _))
  def continueWorkflowRun(rid:String, req:WorkflowRunContinueReq): Future[Try[WorkflowRunContinueRes]] = registry.ask(ContinueWorkflowRun(rid, req, _))

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

  // Temporal workflow management routes
  @POST @Path("/query") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("temporal"),summary = "Query Temporal workflows",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[TemporalQueryReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Query results",
        content = Array(new Content(schema = new Schema(implementation = classOf[QueryResult]))))
    )
  )
  def temporalQueryRoute() = post {
    entity(as[TemporalQueryReq]) { req =>
      complete(temporalQuery(req))
    }
  }

  @POST @Path("/list") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("temporal"),summary = "List Temporal workflows",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[TemporalListReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List results",
        content = Array(new Content(schema = new Schema(implementation = classOf[QueryResult]))))
    )
  )
  def temporalListRoute() = post {
    entity(as[TemporalListReq]) { req =>
      complete(temporalList(req))
    }
  }

  @GET @Path("/describe/{workflowId}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("temporal"), summary = "Describe Temporal workflow",
    parameters = Array(
      new Parameter(name = "workflowId", in = ParameterIn.PATH, description = "Workflow ID"),
      new Parameter(name = "runId", in = ParameterIn.QUERY, description = "Run ID (optional)")
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow execution info",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowExecutionInfo])))),
      new ApiResponse(responseCode = "404", description = "Workflow not found")
    )
  )
  def temporalDescribeRoute() = get {
    path(Segment) { workflowId =>
      parameter("runId".optional) { runId =>
        complete(temporalDescribe(workflowId, runId))
      }
    }
  }

  @GET @Path("/run/{runId}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("temporal"), summary = "Get Temporal workflow by run ID",
    parameters = Array(new Parameter(name = "runId", in = ParameterIn.PATH, description = "Run ID")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow execution info",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowExecutionInfo])))),
      new ApiResponse(responseCode = "404", description = "Workflow not found")
    )
  )
  def temporalGetRoute() = get {
    path(Segment) { runId =>
      complete(temporalGet(runId))
    }
  }

  @POST @Path("/start") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Start PoR Workflow",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowStartReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow started",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowStartRes]))))
    )
  )
  def workflowStartRoute() = post {
    entity(as[WorkflowStartReq]) { req =>
      complete(workflowStart(req))
    }
  }

  @POST @Path("/run/{runId}/signal") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Signal workflow run",
    parameters = Array(new Parameter(name = "runId", in = ParameterIn.PATH, description = "Run ID")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSignalReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Signal delivered",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowSignalRes]))))
    )
  )
  def workflowSignalRoute() = post {
    path(Segment / "signal") { runId =>
      entity(as[WorkflowSignalReq]) { req =>
        complete(workflowSignal(runId, req))
      }
    }
  }

  @POST @Path("/run/{runId}/input") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Update workflow step input",
    parameters = Array(new Parameter(name = "runId", in = ParameterIn.PATH, description = "Run ID")),
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[StepInputReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Step input updated",
        content = Array(new Content(schema = new Schema(implementation = classOf[StepInputRes]))))
    )
  )
  def updateStepInputRoute() = post {
    path(Segment / "input") { runId =>
      entity(as[StepInputReq]) { req =>
        complete(updateStepInput(runId, req))
      }
    }
  }

  @GET @Path("/run") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"), summary = "Return all Workflow Runs",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "List of Workflow Runs",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowRuns]))))
    )
  )
  def getWorkflowRunsRoute() = get {
    complete(getWorkflowRuns())
  }

  @GET @Path("/run/{rid}") @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"), summary = "Return Workflow Run by rid",
    parameters = Array(new Parameter(name = "rid", in = ParameterIn.PATH, description = "Run ID")),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow Run",
        content = Array(new Content(schema = new Schema(implementation = classOf[io.hacken.ext.wf.WorkflowRun])))),
      new ApiResponse(responseCode = "404", description = "Workflow Run not found")
    )
  )
  def getWorkflowRunRoute() = get {
    path(Segment) { (rid) =>
      rejectEmptyResponse {
        complete(getWorkflowRun(rid))
      }
    }
  }

  @POST @Path("/run") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Create Workflow Run",
    requestBody = new RequestBody(content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowRunCreateReq])))),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow Run created",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowRunCreateRes]))))
    )
  )
  def createWorkflowRunRoute() = post {
    entity(as[WorkflowRunCreateReq]) { req =>
      complete(createWorkflowRun(req))
    }
  }

  @PUT @Path("/run/{rid}/{configId}") @Consumes(Array(MediaType.APPLICATION_JSON))
  @Produces(Array(MediaType.APPLICATION_JSON))
  @Operation(tags = Array("workflow"),summary = "Continue Workflow Run from step",
    parameters = Array(
      new Parameter(name = "rid", in = ParameterIn.PATH, description = "Run ID"),
      new Parameter(name = "configId", in = ParameterIn.PATH, description = "DetectorConfig ID")
    ),
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Workflow continued",
        content = Array(new Content(schema = new Schema(implementation = classOf[WorkflowRunContinueRes]))))
    )
  )
  def continueWorkflowRunRoute() = put {
    path(Segment / IntNumber) { (rid, configId) =>
      complete(continueWorkflowRun(rid, WorkflowRunContinueReq(configId)))
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
      },
      pathPrefix("query") {
        temporalQueryRoute()
      },
      pathPrefix("list") {
        temporalListRoute()
      },
      pathPrefix("describe") {
        temporalDescribeRoute()
      },
      pathPrefix("run") {
        concat(
          pathEnd {
            concat(
              getWorkflowRunsRoute(),
              createWorkflowRunRoute()
            )
          },
          workflowSignalRoute(),
          updateStepInputRoute(),
          continueWorkflowRunRoute(),
          getWorkflowRunRoute(),
          temporalGetRoute()
        )
      },
      pathPrefix("start") {
        workflowStartRoute()
      }
    )
  }
}
