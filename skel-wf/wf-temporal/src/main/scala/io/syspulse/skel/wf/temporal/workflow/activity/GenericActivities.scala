package io.syspulse.skel.wf.temporal.workflow.activity

import io.temporal.activity.ActivityInterface
import io.hacken.ext.wf.{WorkflowRun, WorkflowSchema}
import io.hacken.ext.detector.DetectorConfig

/**
 * Generic Activities for Workflow Framework
 *
 * Only contains actual activities that should appear in Temporal UI
 */
@ActivityInterface
trait GenericActivities {

  /**
   * Load workflow execution context (schema + step metadata)
   *
   * Called once at workflow start to load all necessary data
   * Not visible in UI (will be called as local activity)
   *
   * @param schemaId WorkflowSchema ID
   * @param stepConfigIds List of DetectorConfig IDs
   * @return WorkflowExecutionContext with all data needed for execution
   */
  def loadExecutionContext(schemaId: Int, stepConfigIds: Seq[Int]): io.syspulse.skel.wf.temporal.workflow.WorkflowExecutionContext

  /**
   * Execute activity for WorkflowRun
   *
   * This is the ONLY activity that appears in Temporal UI.
   * It uses the business name from DetectorConfig.name
   *
   * @param run WorkflowRun with cursor set to the config ID to execute
   * @return ActivityResult with execution details (rendered as JSON in Events History)
   */
  def executeActivity(run: WorkflowRun): ActivityResult
}

/**
 * Companion object with helper functions
 *
 * These are NOT activities and don't appear in Temporal UI
 */
object GenericActivities {
  import io.syspulse.skel.wf.temporal.workflow.store.{WorkflowSchemaStore, WorkflowRunStore, WorkflowConfigStore}
  import scala.util.{Success, Failure}
  import com.typesafe.scalalogging.Logger

  private val log = Logger(getClass)

  /**
   * Get WorkflowSchema by ID (helper, not an activity)
   */
  def getWorkflowSchema(schemaStore: WorkflowSchemaStore, schemaId: Int): WorkflowSchema = {
    schemaStore.???(schemaId) match {
      case Success(schema) => schema
      case Failure(e) =>
        log.error(s"Failed to get workflow schema ${schemaId}: ${e.getMessage}")
        throw e
    }
  }

  /**
   * Get DetectorConfig name (helper, not an activity)
   */
  def getDetectorConfigName(configStore: WorkflowConfigStore, configId: Int): String = {
    configStore.???(configId) match {
      case Success(config) => config.name
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }
  }

  /**
   * Get step type (helper, not an activity)
   */
  def getStepType(configStore: WorkflowConfigStore, configId: Int): String = {
    val config = configStore.???(configId) match {
      case Success(c) => c
      case Failure(e) =>
        log.error(s"Failed to get detector config ${configId}: ${e.getMessage}")
        throw e
    }
    DetectorConfig.getString(config, "type", "AUTO")
  }

  /**
   * Update WorkflowRun state (helper, not an activity)
   */
  def updateWorkflowRun(runStore: WorkflowRunStore, run: WorkflowRun): WorkflowRun = {
    runStore.+(run) match {
      case Success(updated) => updated
      case Failure(e) =>
        log.error(s"Failed to update workflow run: ${e.getMessage}")
        throw e
    }
  }
}
