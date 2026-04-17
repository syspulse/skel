package io.syspulse.skel.wf.temporal

import spray.json._
import io.syspulse.skel.service.JsonCommon

object TemporalJson extends JsonCommon {
  implicit val jf_workflow_execution_info = jsonFormat9(WorkflowExecutionInfo)
  implicit val jf_query_result = jsonFormat2(QueryResult)
}
