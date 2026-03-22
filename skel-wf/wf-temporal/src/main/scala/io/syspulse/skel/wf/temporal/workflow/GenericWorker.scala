package io.syspulse.skel.wf.temporal.workflow

import com.typesafe.scalalogging.Logger
import scala.util.{Try, Success, Failure}

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.{Worker, WorkerFactory}

import io.syspulse.skel.wf.temporal.Temporal
import io.syspulse.skel.wf.temporal.workflow.activity.GenericActivitiesImpl
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}

/**
 * Generic Worker for Workflow Framework
 *
 * Registers GenericWorkflow and GenericActivities
 */
object GenericWorker {
  val log = Logger(getClass)

  val TASK_QUEUE = "GENERIC_WORKFLOW_QUEUE"

  /**
   * Start Generic Worker
   *
   * @param temporalUri Temporal server URI
   * @param schemaStore WorkflowSchema store
   * @param runStore WorkflowRun store
   * @param configStore DetectorConfig store
   * @return Worker instance
   */
  def run(
    temporalUri: String,
    schemaStore: WorkflowSchemaStore,
    runStore: WorkflowRunStore,
    configStore: WorkflowConfigStore
  ): Try[Worker] = {
    try {
      log.info(s"Starting Generic Worker: ${temporalUri}")

      // Create Temporal client
      import scala.concurrent.ExecutionContext.Implicits.global
      val temporal = new Temporal(temporalUri)
      val service = temporal.getService
      val client = temporal.getClient

      // Create worker factory
      val factory = WorkerFactory.newInstance(client)

      // Create worker for task queue
      val worker = factory.newWorker(TASK_QUEUE)

      // Register workflow implementation
      worker.registerWorkflowImplementationTypes(classOf[GenericWorkflowImpl])
      log.info(s"Registered GenericWorkflow")

      // Register activities implementation
      val activities = new GenericActivitiesImpl(schemaStore, runStore, configStore)
      worker.registerActivitiesImplementations(activities)
      log.info(s"Registered GenericActivities")

      // Register dynamic activity handler (for business names)
      val dynamicActivityHandler = new activity.DynamicActivityHandler(activities)
      worker.registerActivitiesImplementations(dynamicActivityHandler)
      log.info(s"Registered DynamicActivityHandler for business activity names")

      // Start worker
      factory.start()
      log.info(s"Generic Worker started on queue: ${TASK_QUEUE}")

      Success(worker)

    } catch {
      case e: Exception =>
        log.error(s"Failed to start Generic Worker: ${e.getMessage}", e)
        Failure(e)
    }
  }
}
