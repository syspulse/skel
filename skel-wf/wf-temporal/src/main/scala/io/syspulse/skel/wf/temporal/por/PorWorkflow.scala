package io.syspulse.skel.wf.temporal.por

import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod

@WorkflowInterface
trait PorWorkflow {

  @WorkflowMethod
  def execute(run: PorWorkflowRun): PorWorkflowRun
}
