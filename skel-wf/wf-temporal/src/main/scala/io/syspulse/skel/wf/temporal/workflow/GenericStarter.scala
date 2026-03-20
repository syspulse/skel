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
   * Start Generic Workflow
   *
   * @param temporalUri Temporal server URI
   * @param run WorkflowRun to execute
   * @return Future with start result (wid, rid)
   */
  def run(temporalUri: String, run: WorkflowRun)(implicit ec: ExecutionContext): Future[GenericStartResult] = {
    Future {
      log.info(s"Starting Generic Workflow: wid=${run.wid}, schema=${run.schema}, steps=${run.steps}")

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

        // Create workflow stub
        val workflow = client.newWorkflowStub(classOf[GenericWorkflow], options)

        // Start workflow asynchronously
        val execution = WorkflowStub.fromTyped(workflow).start(run)

        // Get run ID
        val runId = WorkflowStub.fromTyped(workflow).getExecution.getRunId

        log.info(s"Generic Workflow started: workflowId=${workflowId}, runId=${runId}")

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
   * Get workflow stub for signaling/querying
   */
  def getWorkflowStub(temporalUri: String, workflowId: String, runId: Option[String] = None): GenericWorkflow = {
    import scala.concurrent.ExecutionContext.Implicits.global
    val temporal = new Temporal(temporalUri)
    val client = temporal.getClient

    runId match {
      case Some(rid) =>
        client.newWorkflowStub(classOf[GenericWorkflow], workflowId, java.util.Optional.of(rid))
      case None =>
        client.newWorkflowStub(classOf[GenericWorkflow], workflowId)
    }
  }
}
