package io.syspulse.skel.wf.temporal.workflow

import io.hacken.ext.wf.WorkflowSchema

/**
 * Workflow Execution Context
 *
 * Contains all data needed for deterministic workflow execution
 * Loaded once at workflow start to avoid I/O during execution
 */
@SerialVersionUID(1L)
case class WorkflowExecutionContext(
  schema: WorkflowSchema,
  stepMetadata: Map[Int, StepMetadata]  // configId -> metadata
) extends Serializable

/**
 * Step Metadata
 *
 * Information about a workflow step needed for execution
 */
@SerialVersionUID(1L)
case class StepMetadata(
  configId: Int,
  name: String,      // Business name (e.g., "ProofOfOwnership")
  stepType: String   // "AUTO" or "WAIT"
) extends Serializable
