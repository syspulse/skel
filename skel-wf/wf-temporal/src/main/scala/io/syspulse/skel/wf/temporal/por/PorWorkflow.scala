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

  /** Generic step input signal - allows updating inputs for any workflow step */
  @SignalMethod
  def updateStepInput(input: StepInput): Unit

  /** Get all step inputs */
  @QueryMethod
  def getStepInputs(): WorkflowInputs

  /** Get specific step input by stepId */
  @QueryMethod
  def getStepInput(stepId: String): Option[StepInput]
}
