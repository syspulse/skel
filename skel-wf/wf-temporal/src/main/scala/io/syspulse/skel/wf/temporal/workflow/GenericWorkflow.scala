package io.syspulse.skel.wf.temporal.workflow

import io.temporal.workflow.{WorkflowInterface, WorkflowMethod, SignalMethod, QueryMethod}
import io.hacken.ext.wf.WorkflowRun

/**
 * Generic Workflow Interface
 *
 * Supports cursor-based multi-step execution with WAIT/AUTO steps
 */
@WorkflowInterface
trait GenericWorkflow {

  /**
   * Execute workflow from WorkflowRun definition
   *
   * @param run WorkflowRun with schema, steps, and cursor
   * @return Updated WorkflowRun with final status
   */
  @WorkflowMethod
  def execute(run: WorkflowRun): WorkflowRun

  /**
   * Signal to continue workflow from WAITING state
   *
   * @param configId DetectorConfig ID to continue from (must match cursor)
   */
  @SignalMethod
  def continueWorkflow(configId: Int): Unit

  /**
   * Query current workflow run state
   *
   * @return Current WorkflowRun
   */
  @QueryMethod
  def getWorkflowRun(): WorkflowRun

  /**
   * Query workflow status
   *
   * @return Current status
   */
  @QueryMethod
  def getStatus(): String

  /**
   * Query current cursor position
   *
   * @return Current cursor (DetectorConfig ID)
   */
  @QueryMethod
  def getCursor(): Int
}
