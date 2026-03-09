package io.syspulse.skel.wf.temporal.por

import io.temporal.workflow.{WorkflowInterface, WorkflowMethod, SignalMethod, QueryMethod}
import spray.json.JsObject

@WorkflowInterface
trait PorWorkflow {

  @WorkflowMethod
  def execute(run: PorWorkflowRun): PorWorkflowRun

  @SignalMethod
  def receivePolSignal(data: JsObject): Unit

  @QueryMethod
  def getPolSignalData(): Option[JsObject]
}
