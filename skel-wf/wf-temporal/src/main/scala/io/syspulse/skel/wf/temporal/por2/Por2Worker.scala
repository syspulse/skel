package io.syspulse.skel.wf.temporal.por2

import com.typesafe.scalalogging.Logger
import scala.util.{Try, Success, Failure}

import io.temporal.worker.Worker

import io.syspulse.skel.wf.temporal.workflow.GenericWorker
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}

/**
 * PoR2 Worker
 *
 * Specialized worker that uses Por2ActivitiesImpl for PoR-specific activity implementations
 */
object Por2Worker {
  val log = Logger(getClass)

  /**
   * Start PoR2 Worker with Por2ActivitiesImpl
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
      log.info(s"Starting PoR2 Worker with Por2ActivitiesImpl")

      // Create PoR2-specific activities implementation
      val activities = new Por2ActivitiesImpl(schemaStore, runStore, configStore)

      // Use GenericWorker with PoR2 activities
      val result = GenericWorker.run(temporalUri, activities)

      result match {
        case Success(worker) =>
          log.info(s"PoR2 Worker started successfully")
          Success(worker)
        case Failure(e) =>
          log.error(s"Failed to start PoR2 Worker: ${e.getMessage}", e)
          Failure(e)
      }

    } catch {
      case e: Exception =>
        log.error(s"Failed to start PoR2 Worker: ${e.getMessage}", e)
        Failure(e)
    }
  }
}
