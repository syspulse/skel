package io.syspulse.skel.wf.temporal.workflow

import com.typesafe.scalalogging.Logger
import scala.concurrent.{Future, ExecutionContext}
import scala.util.{Try, Success, Failure}

import io.temporal.client.{WorkflowOptions, WorkflowStub}
import io.temporal.common.SearchAttributes

import io.syspulse.skel.wf.temporal.Temporal
import io.hacken.ext.wf.WorkflowRun

/**
 * Result of starting a Generic Workflow
 */
case class GenericStartResult(
  workflowId: String,
  runId: String
)

/**
 * Generic Starter for Workflow Framework
 *
 * Starts GenericWorkflow instances from WorkflowRun definitions
 */
object GenericStarter {
  val log = Logger(getClass)

  /**
   * Start Workflow with custom workflow type name
   *
   * @param temporalUri Temporal server URI
   * @param run WorkflowRun to execute (with steps including metadata)
   * @param workflowTypeName Workflow type name to display in Temporal UI (from WorkflowSchema.name)
   * @return Future with start result (wid, rid)
   */
  def run(
    temporalUri: String,
    run: WorkflowRun,
    workflowTypeName: String
  )(implicit ec: ExecutionContext): Future[GenericStartResult] = {
    Future {
      log.info(s"Starting Workflow: type=${workflowTypeName}, wid=${run.wid}, schema=${run.schema}, steps=${run.steps.size}")

      try {
        // Create Temporal client
        val temporal = new Temporal(temporalUri)
        val client = temporal.getClient

        // Build workflow options
        val workflowId = run.wid
        val taskQueue = GenericWorker.TASK_QUEUE

        // Build search attributes
        val searchAttributes = new java.util.HashMap[String, Object]()
        searchAttributes.put("schema", Integer.valueOf(run.schema))
        if (run.rid.isDefined) {
          searchAttributes.put("rid", run.rid.get)
        }

        val options = WorkflowOptions.newBuilder()
          .setWorkflowId(workflowId)
          .setTaskQueue(taskQueue)
          .setTypedSearchAttributes(SearchAttributes.newBuilder().build()) // Use default for now
          .build()

        // Create untyped workflow stub with custom workflow type name
        // This allows the workflow type in Temporal UI to show the business name (e.g., "PoR2 Workflow")
        val workflow = client.newUntypedWorkflowStub(workflowTypeName, options)

        // Start workflow asynchronously
        workflow.start(run)

        // Get run ID
        val runId = workflow.getExecution.getRunId

        log.info(s"Workflow started: type=${workflowTypeName}, workflowId=${workflowId}, runId=${runId}")

        // Shutdown client
        temporal.shutdown()

        GenericStartResult(workflowId = workflowId, runId = runId)

      } catch {
        case e: Exception =>
          log.error(s"Failed to start Generic Workflow: ${e.getMessage}", e)
          throw e
      }
    }
  }

  /**
   * Get untyped workflow stub for signaling/querying
   *
   * @param temporalUri Temporal server URI
   * @param workflowId Workflow ID
   * @param runId Optional Run ID
   * @return WorkflowStub for signaling/querying
   */
  def getWorkflowStub(temporalUri: String, workflowId: String, runId: Option[String] = None): WorkflowStub = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val temporal = new Temporal(temporalUri)
    val client = temporal.getClient

    runId match {
      case Some(rid) =>
        client.newUntypedWorkflowStub(workflowId, java.util.Optional.of(rid), java.util.Optional.empty())
      case None =>
        client.newUntypedWorkflowStub(workflowId, java.util.Optional.empty(), java.util.Optional.empty())
    }
  }
}
