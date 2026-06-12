package io.syspulse.skel.wf.ext.server

import spray.json._
import io.syspulse.skel.service.JsonCommon

object WorkflowJson extends JsonCommon {
  import io.hacken.ext.wf.WorkflowGrafJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.detector.DetectorSchemaJson._
  import io.hacken.ext.detector.DetectorConfigJson._

  implicit val jf_action: RootJsonFormat[WorkflowActionRes] = jsonFormat2(WorkflowActionRes)

  // ---- WorkflowSchema ----
  implicit val jf_schemas: RootJsonFormat[WorkflowSchemas]               = jsonFormat3(WorkflowSchemas)
  implicit val jf_schema_view: RootJsonFormat[WorkflowSchemaView]        = jsonFormat2(WorkflowSchemaView)
  implicit val jf_schema_create: RootJsonFormat[WorkflowSchemaCreateReq] = jsonFormat9(WorkflowSchemaCreateReq)
  implicit val jf_schema_update: RootJsonFormat[WorkflowSchemaUpdateReq] = jsonFormat8(WorkflowSchemaUpdateReq)
  implicit val jf_schema_dsl: RootJsonFormat[WorkflowSchemaDslReq]       = jsonFormat3(WorkflowSchemaDslReq)

  // ---- WorkflowConfig ----
  implicit val jf_configs: RootJsonFormat[WorkflowConfigs]               = jsonFormat3(WorkflowConfigs)
  implicit val jf_config_view: RootJsonFormat[WorkflowConfigView]        = jsonFormat2(WorkflowConfigView)
  implicit val jf_config_create: RootJsonFormat[WorkflowConfigCreateReq] = jsonFormat5(WorkflowConfigCreateReq)
  implicit val jf_config_update: RootJsonFormat[WorkflowConfigUpdateReq] = jsonFormat11(WorkflowConfigUpdateReq)
  implicit val jf_config_dsl: RootJsonFormat[WorkflowConfigDslReq]       = jsonFormat3(WorkflowConfigDslReq)

  // ---- WorkflowGraf ----
  implicit val jf_grafs: RootJsonFormat[WorkflowGrafs]                   = jsonFormat2(WorkflowGrafs)
  implicit val jf_graf_create: RootJsonFormat[WorkflowGrafCreateReq]     = jsonFormat4(WorkflowGrafCreateReq)
}
