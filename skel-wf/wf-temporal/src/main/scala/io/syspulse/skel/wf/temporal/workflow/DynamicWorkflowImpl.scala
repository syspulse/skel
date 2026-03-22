package io.syspulse.skel.wf.temporal.workflow

import com.typesafe.scalalogging.Logger
import io.temporal.workflow.{DynamicWorkflow, Workflow}
import io.temporal.common.converter.EncodedValues

import io.hacken.ext.wf.WorkflowRun

/**
 * Dynamic Workflow Implementation
 *
 * Handles workflows with custom type names (from WorkflowSchema.name)
 * Delegates actual execution to GenericWorkflowImpl logic
 */
class DynamicWorkflowImpl extends DynamicWorkflow {

  private val log = Logger(getClass)

  // Delegate to GenericWorkflowImpl for actual logic
  private val impl = new GenericWorkflowImpl()

  /**
   * Execute workflow with dynamic type name
   *
   * This allows workflow types in Temporal UI to show business names
   * (e.g., "PoR2 Workflow", "KYC Workflow") instead of "GenericWorkflow"
   */
  override def execute(args: EncodedValues): Object = {
    val workflowType = Workflow.getInfo().getWorkflowType
    log.info(s"Executing dynamic workflow: type=${workflowType}")

    // Decode the WorkflowRun argument
    val run = args.get(0, classOf[WorkflowRun])
    log.info(s"WorkflowRun: wid=${run.wid}, schema=${run.schema}, steps=${run.steps.size}")

    // Execute using GenericWorkflowImpl logic
    val result = impl.execute(run)
    log.info(s"Workflow ${workflowType} completed: status=${result.status}")

    result
  }
}
