package io.syspulse.skel.wf.temporal.workflow.activity

import io.temporal.activity.ActivityInterface
import io.hacken.ext.wf.{WorkflowRun, WorkflowSchema}
import io.hacken.ext.detector.DetectorConfig

/**
 * Generic Activities for Workflow Framework
 *
 * Provides CRUD operations for workflow execution
 */
@ActivityInterface
trait GenericActivities {

  /**
   * Get WorkflowSchema by ID
   */
  def getWorkflowSchema(schemaId: Int): WorkflowSchema

  /**
   * Get DetectorConfig by ID (returns only ID for verification)
   */
  def getDetectorConfig(configId: Int): Int

  /**
   * Get DetectorConfig name (business name for activity display)
   *
   * @param configId DetectorConfig ID
   * @return Config name (e.g., "ProofOfOwnership", "ProofOfReserve")
   */
  def getDetectorConfigName(configId: Int): String

  /**
   * Update WorkflowRun state
   */
  def updateWorkflowRun(run: WorkflowRun): WorkflowRun

  /**
   * Get step type from DetectorConfig by ID
   *
   * @return "WAIT" or "AUTO"
   */
  def getStepType(configId: Int): String

  /**
   * Execute activity for DetectorConfig by ID
   *
   * Maps config.name to activity implementation and executes it
   *
   * @return Config ID after execution
   */
  def executeActivity(configId: Int): Int
}
