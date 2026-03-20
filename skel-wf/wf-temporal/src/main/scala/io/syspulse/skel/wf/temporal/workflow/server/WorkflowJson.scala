package io.syspulse.skel.wf.temporal.workflow.server

import spray.json._
import io.syspulse.skel.service.JsonCommon
import io.hacken.ext.wf.WorkflowSchemaJson._
import io.syspulse.skel.wf.temporal.TemporalJson._
import io.syspulse.skel.wf.temporal.por.{StepInput, WorkflowInputs}
import io.hacken.ext.wf.WorkflowRunJson._

object WorkflowJson extends JsonCommon {
  implicit val jf_workflow_res = jsonFormat1(WorkflowRes)
  implicit val jf_workflows = jsonFormat2(Workflows)
  implicit val jf_workflow_create_req = jsonFormat10(WorkflowCreateReq)
  implicit val jf_workflow_update_req = jsonFormat7(WorkflowUpdateReq)
  implicit val jf_temporal_query_req = jsonFormat2(TemporalQueryReq)
  implicit val jf_temporal_list_req = jsonFormat3(TemporalListReq)
  implicit val jf_workflow_start_req = jsonFormat2(WorkflowStartReq)
  implicit val jf_workflow_start_res = jsonFormat2(WorkflowStartRes)
  implicit val jf_workflow_signal_req = jsonFormat2(WorkflowSignalReq)
  implicit val jf_workflow_signal_res = jsonFormat2(WorkflowSignalRes)

  // Step input formats
  implicit val jf_step_input = StepInput.format
  implicit val jf_workflow_inputs = WorkflowInputs.format
  implicit val jf_step_input_req = jsonFormat2(StepInputReq)
  implicit val jf_step_input_res = jsonFormat2(StepInputRes)

  // Workflow Run formats
  implicit val jf_workflow_run_create_req = jsonFormat2(WorkflowRunCreateReq)
  implicit val jf_workflow_run_create_res = jsonFormat2(WorkflowRunCreateRes)
  implicit val jf_workflow_run_continue_req = jsonFormat1(WorkflowRunContinueReq)
  implicit val jf_workflow_run_continue_res = jsonFormat2(WorkflowRunContinueRes)
  implicit val jf_workflow_runs = jsonFormat2(WorkflowRuns)
}
