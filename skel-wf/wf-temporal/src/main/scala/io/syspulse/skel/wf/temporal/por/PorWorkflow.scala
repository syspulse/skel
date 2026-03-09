package io.syspulse.skel.wf.temporal.por

import io.temporal.workflow.{WorkflowInterface, WorkflowMethod, SignalMethod, QueryMethod}

@WorkflowInterface
trait PorWorkflow {

  @WorkflowMethod
  def execute(run: PorWorkflowRun): PorWorkflowRun

  @SignalMethod
  def signalPol(data: PolFileData): Unit

  @QueryMethod
  def getPolSignalData(): Option[PolFileData]
}
