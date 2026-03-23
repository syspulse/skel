package io.syspulse.skel.wf.temporal.demo

import scala.util.Try
import io.temporal.worker.Worker

import io.syspulse.skel.wf.temporal.workflow.GenericWorker
import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}

/**
 * Demo Worker
 *
 * Starts a worker with DemoActivitiesImpl
 */
object DemoWorker {

  /**
   * Start Demo Worker
   *
   * @param temporalUri Temporal server URI
   * @param schemaStore WorkflowSchemaStore
   * @param runStore WorkflowRunStore
   * @param configStore WorkflowConfigStore
   * @return Try[Worker]
   */
  def run(
    temporalUri: String,
    schemaStore: WorkflowSchemaStore,
    runStore: WorkflowRunStore,
    configStore: WorkflowConfigStore
  ): Try[Worker] = {

    val activities = new DemoActivitiesImpl(schemaStore, runStore, configStore)

    GenericWorker.run(temporalUri, activities)
  }
}
