package io.syspulse.skel.wf

import io.syspulse.skel.service.JsonCommon

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.wf.runtime.ExecData
import io.syspulse.skel.wf.runtime.Executing
import io.syspulse.skel.wf.runtime.Executing.ID
import io.syspulse.skel.wf.runtime.Workflowing

object WorkflowJson extends JsonCommon {
  implicit val jf_ExecData = jsonFormat1(ExecData.apply _)
  //implicit val jf_wfid = jsonFormat2(Workflowing.ID)
  //implicit val jf_ExecId = jsonFormat2(Executing.ID)
  implicit val jf_states = jsonFormat4(State)
  implicit val jf_wfs = jsonFormat5(WorkflowState.apply _)
}
