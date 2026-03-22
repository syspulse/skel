package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.ActorContext

import io.syspulse.skel.util.Util
import io.syspulse.skel.Command

import io.hacken.ext.wf.WorkflowSchema
import io.syspulse.skel.wf.temporal.workflow.server._
import io.syspulse.skel.wf.temporal._
import io.syspulse.skel.wf.temporal.por._
import io.syspulse.skel.wf.temporal.por.demo.DemoUtil

object WorkflowRegistry {
  val log = Logger(s"${this}")

  final case class GetWorkflow(id:Int, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class GetWorkflows(replyTo: ActorRef[Try[Workflows]]) extends Command
  final case class CreateWorkflow(req:WorkflowCreateReq, replyTo: ActorRef[Try[WorkflowRes]]) extends Command
  final case class UpdateWorkflow(id:Int, req:WorkflowUpdateReq, replyTo: ActorRef[Try[WorkflowRes]]) extends Command
  final case class DeleteWorkflow(id:Int, replyTo: ActorRef[Try[WorkflowRes]]) extends Command

  // Temporal workflow management commands
  final case class TemporalQuery(req:TemporalQueryReq, replyTo: ActorRef[Try[QueryResult]]) extends Command
  final case class TemporalList(req:TemporalListReq, replyTo: ActorRef[Try[QueryResult]]) extends Command
  final case class TemporalDescribe(workflowId:String, runId:Option[String], replyTo: ActorRef[Try[WorkflowExecutionInfo]]) extends Command
  final case class TemporalGet(runId:String, replyTo: ActorRef[Try[WorkflowExecutionInfo]]) extends Command
  final case class WorkflowStart(req:WorkflowStartReq, replyTo: ActorRef[Try[WorkflowStartRes]]) extends Command
  final case class WorkflowSignal(runId:String, req:WorkflowSignalReq, replyTo: ActorRef[Try[WorkflowSignalRes]]) extends Command
  final case class UpdateStepInput(runId:String, req:StepInputReq, replyTo: ActorRef[Try[StepInputRes]]) extends Command

  // Workflow Run commands
  final case class GetWorkflowRun(rid:String, replyTo: ActorRef[Try[io.hacken.ext.wf.WorkflowRun]]) extends Command
  final case class GetWorkflowRuns(replyTo: ActorRef[Try[WorkflowRuns]]) extends Command
  final case class CreateWorkflowRun(req:WorkflowRunCreateReq, replyTo: ActorRef[Try[WorkflowRunCreateRes]]) extends Command
  final case class ContinueWorkflowRun(rid:String, req:WorkflowRunContinueReq, replyTo: ActorRef[Try[WorkflowRunContinueRes]]) extends Command

  // Internal response messages for async operations
  private final case class TemporalQueryResponse(result: Try[QueryResult], replyTo: ActorRef[Try[QueryResult]]) extends Command
  private final case class TemporalListResponse(result: Try[QueryResult], replyTo: ActorRef[Try[QueryResult]]) extends Command
  private final case class TemporalDescribeResponse(result: Try[WorkflowExecutionInfo], replyTo: ActorRef[Try[WorkflowExecutionInfo]]) extends Command
  private final case class TemporalGetResponse(result: Try[WorkflowExecutionInfo], replyTo: ActorRef[Try[WorkflowExecutionInfo]]) extends Command
  private final case class WorkflowStartResponse(result: Try[WorkflowStartRes], replyTo: ActorRef[Try[WorkflowStartRes]]) extends Command
  private final case class WorkflowSignalResponse(result: Try[WorkflowSignalRes], replyTo: ActorRef[Try[WorkflowSignalRes]]) extends Command
  private final case class UpdateStepInputResponse(result: Try[StepInputRes], replyTo: ActorRef[Try[StepInputRes]]) extends Command
  private final case class GetWorkflowRunResponse(result: Try[io.hacken.ext.wf.WorkflowRun], replyTo: ActorRef[Try[io.hacken.ext.wf.WorkflowRun]]) extends Command
  private final case class CreateWorkflowRunResponse(result: Try[WorkflowRunCreateRes], replyTo: ActorRef[Try[WorkflowRunCreateRes]]) extends Command
  private final case class ContinueWorkflowRunResponse(result: Try[WorkflowRunContinueRes], replyTo: ActorRef[Try[WorkflowRunContinueRes]]) extends Command

  def apply(store: WorkflowSchemaStore, runStore: WorkflowRunStore, configStore: WorkflowConfigStore, engineUri: String): Behavior[io.syspulse.skel.Command] = {
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.executionContext
      registry(store, runStore, configStore, engineUri, context)
    }
  }

  private def registry(store: WorkflowSchemaStore, runStore: WorkflowRunStore, configStore: WorkflowConfigStore, engineUri: String, context: ActorContext[io.syspulse.skel.Command])(implicit ec: ExecutionContext): Behavior[io.syspulse.skel.Command] = {
    Behaviors.receiveMessage {

      case GetWorkflow(id, replyTo) =>
        val r = store.???(id)
        replyTo ! r
        Behaviors.same

      case GetWorkflows(replyTo) =>
        val r = store.all
        replyTo ! Success(Workflows(r, total = Some(r.size)))
        Behaviors.same

      case UpdateWorkflow(id, req, replyTo) =>
        log.info(s"UpdateWorkflow($id),${req.name},${req.title}")

        val r = store
          .???(id)
          .map(w => w.copy(
            updatedAt = System.currentTimeMillis(),
            name = if(req.name.isDefined) req.name.get else w.name,
            title = if(req.title.isDefined) req.title.get else w.title,
            description = if(req.description.isDefined) req.description.get else w.description,
            version = if(req.version.isDefined) req.version.get else w.version,
            tags = if(req.tags.isDefined) req.tags.get else w.tags,
            nodes = if(req.nodes.isDefined) req.nodes.get else w.nodes,
            connections = if(req.connections.isDefined) req.connections.get else w.connections
          ))
          .flatMap(w => store.+(w))

        r match {
          case Success(w) =>
            replyTo ! Success(WorkflowRes(w.id))
          case Failure(e) =>
            log.error(s"failed to update workflow: ${id}", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case CreateWorkflow(req, replyTo) =>
        log.info(s"CreateWorkflow,${req.name}")

        // Find max ID and increment
        val maxId = if (store.all.isEmpty) 0 else store.all.map(_.id).max
        val newId = maxId + 1

        val r = store.+(
          WorkflowSchema(
            id = newId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = "ACTIVE",
            name = req.name,
            version = req.version.getOrElse("1.0.0"),
            title = req.title.getOrElse(req.name),
            description = req.description.getOrElse(""),
            author = req.author.getOrElse(""),
            icon = req.icon,
            faq = req.faq,
            tags = req.tags.getOrElse(Seq.empty),
            nodes = req.nodes.getOrElse(Seq.empty),
            connections = req.connections.getOrElse(Seq.empty)
          )
        )

        r match {
          case Success(w) =>
            replyTo ! Success(WorkflowRes(w.id))
          case Failure(e) =>
            log.error(s"failed to create workflow", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case DeleteWorkflow(id, replyTo) =>
        val r = store.del(id)

        r match {
          case Success(_) =>
            replyTo ! Success(WorkflowRes(id))
          case Failure(e) =>
            log.error(s"failed to delete workflow: ${id}", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case TemporalQuery(req, replyTo) =>
        log.info(s"TemporalQuery(${req.query}, pageSize=${req.pageSize})")
        context.pipeToSelf(Temporal.query(engineUri, req.query, req.pageSize)) {
          case Success(result) => TemporalQueryResponse(Success(result), replyTo)
          case Failure(e) => TemporalQueryResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case TemporalQueryResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case TemporalList(req, replyTo) =>
        log.info(s"TemporalList(status=${req.status}, workflowType=${req.workflowType}, pageSize=${req.pageSize})")
        context.pipeToSelf(Temporal.list(engineUri, req.status, req.workflowType, req.pageSize)) {
          case Success(result) => TemporalListResponse(Success(result), replyTo)
          case Failure(e) => TemporalListResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case TemporalListResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case TemporalDescribe(workflowId, runId, replyTo) =>
        log.info(s"TemporalDescribe(workflowId=$workflowId, runId=$runId)")
        context.pipeToSelf(Temporal.describe(engineUri, workflowId, runId)) {
          case Success(result) => TemporalDescribeResponse(Success(result), replyTo)
          case Failure(e) => TemporalDescribeResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case TemporalDescribeResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case TemporalGet(runId, replyTo) =>
        log.info(s"TemporalGet(runId=$runId)")
        context.pipeToSelf(Temporal.get(engineUri, runId)) {
          case Success(result) => TemporalGetResponse(Success(result), replyTo)
          case Failure(e) => TemporalGetResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case TemporalGetResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case WorkflowStart(req, replyTo) =>
        log.info(s"WorkflowStart(src=${req.src}, data=${req.data})")
        context.pipeToSelf(startWorkflow(engineUri, req)) {
          case Success(result) => WorkflowStartResponse(Success(WorkflowStartRes(result.workflowId, result.runId)), replyTo)
          case Failure(e) => WorkflowStartResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case WorkflowStartResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case WorkflowSignal(runId, req, replyTo) =>
        log.info(s"WorkflowSignal(runId=$runId, aid=${req.aid})")
        // Send Temporal signal to workflow
        context.pipeToSelf(signalWorkflow(engineUri, runId, req)) {
          case Success(result) => WorkflowSignalResponse(Success(result), replyTo)
          case Failure(e) => WorkflowSignalResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case WorkflowSignalResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case UpdateStepInput(runId, req, replyTo) =>
        log.info(s"UpdateStepInput(runId=$runId, stepId=${req.stepId})")
        // Update step input in workflow
        context.pipeToSelf(updateStepInput(engineUri, runId, req)) {
          case Success(result) => UpdateStepInputResponse(Success(result), replyTo)
          case Failure(e) => UpdateStepInputResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case UpdateStepInputResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case GetWorkflowRun(rid, replyTo) =>
        // Query WorkflowRun from Temporal engine (not store)
        context.pipeToSelf(Temporal.queryWorkflowRunByRunId(engineUri, rid)) {
          case Success(run) => GetWorkflowRunResponse(Success(run), replyTo)
          case Failure(e) => GetWorkflowRunResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case GetWorkflowRunResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case GetWorkflowRuns(replyTo) =>
        val r = runStore.all
        replyTo ! Success(WorkflowRuns(r, total = Some(r.size)))
        Behaviors.same

      case CreateWorkflowRun(req, replyTo) =>
        log.info(s"CreateWorkflowRun(schemaId=${req.schemaId}, steps=${req.steps})")
        context.pipeToSelf(createWorkflowRun(engineUri, store, runStore, configStore, req)) {
          case Success(result) => CreateWorkflowRunResponse(Success(result), replyTo)
          case Failure(e) => CreateWorkflowRunResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case CreateWorkflowRunResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same

      case ContinueWorkflowRun(rid, req, replyTo) =>
        log.info(s"ContinueWorkflowRun(rid=$rid, configId=${req.configId})")
        context.pipeToSelf(continueWorkflowRun(engineUri, runStore, rid, req)) {
          case Success(result) => ContinueWorkflowRunResponse(Success(result), replyTo)
          case Failure(e) => ContinueWorkflowRunResponse(Failure(e), replyTo)
        }
        Behaviors.same

      case ContinueWorkflowRunResponse(result, replyTo) =>
        replyTo ! result
        Behaviors.same
    }
  }

  private def startWorkflow(engineUri: String, req: WorkflowStartReq)(implicit ec: ExecutionContext): Future[PorStartResult] = {
    val run = req.src match {
      case "demo" =>
        // Generate demo flow using DemoUtil
        // data field contains flow name (flow-1, flow-2, etc.)
        DemoUtil.generateFlowRun(
          flow = req.data,
          proj = "demo",
          tags = Seq.empty,
          memo = Map.empty,
          polSignalMode = "simulate"
        )

      case "data" =>
        // Parse JSON from data field
        val mapper = new com.fasterxml.jackson.databind.ObjectMapper()
        mapper.registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)
        mapper.readValue(req.data, classOf[PorWorkflowRun])

      case "file" =>
        // Load from file path
        val json = os.read(os.Path(req.data, os.pwd))
        val mapper = new com.fasterxml.jackson.databind.ObjectMapper()
        mapper.registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)
        mapper.readValue(json, classOf[PorWorkflowRun])

      case "url" =>
        // Download from URL
        val response = requests.get(req.data)
        val json = response.text()
        val mapper = new com.fasterxml.jackson.databind.ObjectMapper()
        mapper.registerModule(com.fasterxml.jackson.module.scala.DefaultScalaModule)
        mapper.readValue(json, classOf[PorWorkflowRun])

      case _ =>
        throw new IllegalArgumentException(s"Unknown source type: ${req.src}")
    }

    // Start the workflow (returns Future)
    PorStarter.run(engineUri, run)
  }

  private def signalWorkflow(engineUri: String, runId: String, req: WorkflowSignalReq)(implicit ec: ExecutionContext): Future[WorkflowSignalRes] = {
    // Map activity ID to signal name
    val signalName = req.aid.toLowerCase match {
      case "pol" => "receivePolSignal"
      case _ =>
        return Future.successful(WorkflowSignalRes(success = false, message = s"Unknown activity: ${req.aid}"))
    }

    // Use Temporal client to send signal
    Temporal.signalByRunId(engineUri, runId, signalName, req.data).map { message =>
      WorkflowSignalRes(success = true, message = message)
    }.recover {
      case e: Exception =>
        log.error(s"Failed to signal workflow runId=$runId: ${e.getMessage}", e)
        WorkflowSignalRes(success = false, message = s"Failed to signal workflow: ${e.getMessage}")
    }
  }

  private def updateStepInput(engineUri: String, runId: String, req: StepInputReq)(implicit ec: ExecutionContext): Future[StepInputRes] = {
    import spray.json._

    // Create StepInput object from request
    val stepInput = StepInput(
      stepId = req.stepId,
      data = req.data
    )

    // Convert to JsObject for Temporal signal
    val signalData = stepInput.toJson.asJsObject

    // Use Temporal client to send updateStepInput signal
    Temporal.signalByRunId(engineUri, runId, "updateStepInput", signalData).map { message =>
      StepInputRes(success = true, message = message)
    }.recover {
      case e: Exception =>
        log.error(s"Failed to update step input runId=$runId, stepId=${req.stepId}: ${e.getMessage}", e)
        StepInputRes(success = false, message = s"Failed to update step input: ${e.getMessage}")
    }
  }

  private def createWorkflowRun(engineUri: String, schemaStore: WorkflowSchemaStore, runStore: WorkflowRunStore, configStore: WorkflowConfigStore, req: WorkflowRunCreateReq)(implicit ec: ExecutionContext): Future[WorkflowRunCreateRes] = {
    import io.syspulse.skel.wf.temporal.workflow.GenericStarter
    import io.hacken.ext.detector.DetectorConfig
    import io.hacken.ext.wf.WorkflowStep

    // Generate workflow ID
    val wid = s"workflow-${req.schemaId}-${System.currentTimeMillis()}"

    // Get workflow schema name for Temporal workflow type
    val workflowTypeName = schemaStore.??(req.schemaId) match {
      case Some(schema) => schema.name
      case None =>
        log.warn(s"Schema ${req.schemaId} not found, using default workflow type")
        "GenericWorkflow"
    }

    // Build workflow steps with metadata from config store
    val workflowSteps: Seq[WorkflowStep] = req.steps.flatMap { configId =>
      configStore.???(configId).toOption.map { config =>
        WorkflowStep(
          id = configId,
          name = config.name,
          typ = DetectorConfig.getString(config, "type", "AUTO")
        )
      }
    }

    // Create WorkflowRun with NEW status (no rid yet)
    val workflowRun = io.hacken.ext.wf.WorkflowRun(
      wid = wid,
      rid = None,  // Will be set by Temporal
      status = "NEW",
      cursor = -1,  // Not started yet
      schema = req.schemaId,
      steps = workflowSteps
    )

    // Store the workflow run
    runStore.+(workflowRun) match {
      case Success(_) =>
        log.info(s"WorkflowRun stored: ${wid}, workflow type: ${workflowTypeName}")

        // Start Temporal workflow with schema name as workflow type
        GenericStarter.run(engineUri, workflowRun, workflowTypeName).map { result =>
          log.info(s"Temporal workflow started: workflowId=${result.workflowId}, runId=${result.runId}")

          // Update WorkflowRun with rid and status
          val updatedRun = workflowRun.copy(
            rid = Some(result.runId),
            status = "RUNNING"
          )
          runStore.+(updatedRun)

          WorkflowRunCreateRes(wid = result.workflowId, rid = result.runId)

        }.recover {
          case e: Exception =>
            log.error(s"Failed to start Temporal workflow: ${e.getMessage}", e)

            // Update status to FAILED
            val failedRun = workflowRun.copy(status = "FAILED")
            runStore.+(failedRun)

            throw e
        }

      case Failure(e) =>
        log.error(s"Failed to store WorkflowRun: ${e.getMessage}", e)
        Future.failed(e)
    }
  }

  private def continueWorkflowRun(engineUri: String, runStore: WorkflowRunStore, rid: String, req: WorkflowRunContinueReq)(implicit ec: ExecutionContext): Future[WorkflowRunContinueRes] = {
    import spray.json._

    // Get workflow execution info from Temporal engine
    Temporal.get(engineUri, rid).flatMap { info =>
      log.info(s"ContinueWorkflowRun(rid=$rid, workflowId=${info.workflowId}, status=${info.status})")

      // Check if workflow is in a valid state to continue
      // Temporal uses WORKFLOW_EXECUTION_STATUS_RUNNING, not just "RUNNING"
      if (info.status == "WORKFLOW_EXECUTION_STATUS_RUNNING") {
        // Query WorkflowRun state from workflow to verify cursor
        Temporal.queryWorkflowRunByRunId(engineUri, rid).flatMap { run =>
          log.info(s"WorkflowRun state: cursor=${run.cursor}, status=${run.status}")

          // Check if cursor matches configId (optional verification)
          if (run.cursor == req.configId || run.status == "WAITING") {
            // Send continue signal to Temporal workflow
            val signalData = JsObject("configId" -> JsNumber(req.configId))
            Temporal.signalByRunId(engineUri, rid, "continueWorkflow", signalData).map { message =>
              WorkflowRunContinueRes(success = true, message = message)
            }.recover {
              case e: Exception =>
                log.error(s"Failed to continue workflow rid=$rid, configId=${req.configId}: ${e.getMessage}", e)
                WorkflowRunContinueRes(success = false, message = s"Failed to continue workflow: ${e.getMessage}")
            }
          } else {
            // Cursor mismatch
            log.warn(s"Cursor mismatch: cursor=${run.cursor}, configId=${req.configId}, status=${run.status}")
            Future.successful(WorkflowRunContinueRes(
              success = false,
              message = s"Cursor mismatch: workflow is at step ${run.cursor}, not ${req.configId}"
            ))
          }
        }
      } else {
        // Workflow is not in RUNNING state
        log.warn(s"Workflow not in RUNNING state: status=${info.status}")
        Future.successful(WorkflowRunContinueRes(
          success = false,
          message = s"Workflow is not running (status=${info.status})"
        ))
      }
    }.recover {
      case e: NoSuchElementException =>
        log.error(s"Workflow not found in Temporal engine: rid=$rid", e)
        WorkflowRunContinueRes(
          success = false,
          message = s"Workflow not found in engine: ${e.getMessage}"
        )
      case e: Exception =>
        log.error(s"Failed to query workflow from engine: rid=$rid", e)
        WorkflowRunContinueRes(
          success = false,
          message = s"Failed to query workflow: ${e.getMessage}"
        )
    }
  }
}
