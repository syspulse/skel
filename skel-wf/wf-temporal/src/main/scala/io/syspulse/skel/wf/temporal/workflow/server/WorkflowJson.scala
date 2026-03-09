package io.syspulse.skel.wf.temporal.workflow.server

import spray.json._
import io.syspulse.skel.service.JsonCommon
import io.hacken.ext.wf.WorkflowSchemaJson._

object WorkflowJson extends JsonCommon {
  implicit val jf_workflow_res = jsonFormat1(WorkflowRes)
  implicit val jf_workflows = jsonFormat2(Workflows)
  implicit val jf_workflow_create_req = jsonFormat10(WorkflowCreateReq)
  implicit val jf_workflow_update_req = jsonFormat7(WorkflowUpdateReq)
}
