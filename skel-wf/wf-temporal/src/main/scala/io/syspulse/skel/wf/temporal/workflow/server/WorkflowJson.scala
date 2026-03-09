package io.syspulse.skel.wf.temporal.workflow.server

import spray.json._
import io.syspulse.skel.service.JsonCommon
import io.hacken.ext.wf.WorkflowSchemaJson._
import io.syspulse.skel.wf.temporal.TemporalJson._

object WorkflowJson extends JsonCommon {
  implicit val jf_workflow_res = jsonFormat1(WorkflowRes)
  implicit val jf_workflows = jsonFormat2(Workflows)
  implicit val jf_workflow_create_req = jsonFormat10(WorkflowCreateReq)
  implicit val jf_workflow_update_req = jsonFormat7(WorkflowUpdateReq)
  implicit val jf_temporal_query_req = jsonFormat2(TemporalQueryReq)
  implicit val jf_temporal_list_req = jsonFormat3(TemporalListReq)
  implicit val jf_workflow_start_req = jsonFormat2(WorkflowStartReq)
  implicit val jf_workflow_start_res = jsonFormat2(WorkflowStartRes)
}
