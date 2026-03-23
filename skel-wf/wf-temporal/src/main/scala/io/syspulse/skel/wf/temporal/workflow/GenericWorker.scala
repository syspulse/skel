package io.syspulse.skel.wf.temporal.workflow

import com.typesafe.scalalogging.Logger
import scala.util.{Try, Success, Failure}

import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.worker.{Worker, WorkerFactory}

import io.syspulse.skel.wf.temporal.Temporal
import io.syspulse.skel.wf.temporal.workflow.activity.{GenericActivities, GenericActivitiesImpl}
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}

/**
 * Generic Worker for Workflow Framework
 *
 * Registers GenericWorkflow and GenericActivities
 */
object GenericWorker {
  val log = Logger(getClass)

  val DEFAULT_TASK_QUEUE = "GENERIC_WORKFLOW_QUEUE"

  /**
   * Start Generic Worker with custom activities implementation
   *
   * @param temporalUri Temporal server URI
   * @param activities GenericActivities implementation (can be GenericActivitiesImpl or Por2ActivitiesImpl)
   * @param taskQueue Task queue name (default: GENERIC_WORKFLOW_QUEUE)
   * @return Worker instance
   */
  def run(
    temporalUri: String,
    activities: GenericActivities,
    taskQueue: String = DEFAULT_TASK_QUEUE
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
      val worker = factory.newWorker(taskQueue)

      // Register dynamic workflow implementation (handles any workflow type name)
      worker.registerWorkflowImplementationTypes(classOf[DynamicWorkflowImpl])
      log.info(s"Registered DynamicWorkflow (handles custom workflow type names)")

      // Register activities implementation
      worker.registerActivitiesImplementations(activities)
      log.info(s"Registered activities: ${activities.getClass.getSimpleName}")

      // Register dynamic activity handler (for business names)
      val dynamicActivityHandler = new activity.DynamicActivityHandler(activities)
      worker.registerActivitiesImplementations(dynamicActivityHandler)
      log.info(s"Registered DynamicActivityHandler for business activity names")

      // Start worker
      factory.start()
      log.info(s"Generic Worker started on queue: ${taskQueue}")

      Success(worker)

    } catch {
      case e: Exception =>
        log.error(s"Failed to start Generic Worker: ${e.getMessage}", e)
        Failure(e)
    }
  }

  /**
   * Start Generic Worker with default GenericActivitiesImpl
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
    val activities = new GenericActivitiesImpl(schemaStore, runStore, configStore)
    run(temporalUri, activities)
  }
}
