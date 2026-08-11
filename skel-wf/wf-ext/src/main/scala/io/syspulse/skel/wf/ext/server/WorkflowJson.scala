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
  implicit val jf_schema_create: RootJsonFormat[WorkflowSchemaCreateReq] = jsonFormat11(WorkflowSchemaCreateReq)
  implicit val jf_schema_update: RootJsonFormat[WorkflowSchemaUpdateReq] = jsonFormat11(WorkflowSchemaUpdateReq)
  implicit val jf_schema_dsl: RootJsonFormat[WorkflowSchemaDslReq]       = jsonFormat3(WorkflowSchemaDslReq)

  // ---- WorkflowConfig ----
  implicit val jf_configs: RootJsonFormat[WorkflowConfigs]               = jsonFormat4(WorkflowConfigs)
  implicit val jf_config_view: RootJsonFormat[WorkflowConfigView]        = jsonFormat3(WorkflowConfigView)
  implicit val jf_config_create: RootJsonFormat[WorkflowConfigCreateReq] = jsonFormat5(WorkflowConfigCreateReq)
  implicit val jf_config_update: RootJsonFormat[WorkflowConfigUpdateReq] = jsonFormat13(WorkflowConfigUpdateReq)
  implicit val jf_config_dsl: RootJsonFormat[WorkflowConfigDslReq]       = jsonFormat3(WorkflowConfigDslReq)

  // ---- WorkflowGraf ----
  implicit val jf_grafs: RootJsonFormat[WorkflowGrafs]                   = jsonFormat2(WorkflowGrafs)
  implicit val jf_graf_create: RootJsonFormat[WorkflowGrafCreateReq]     = jsonFormat4(WorkflowGrafCreateReq)

  // ---- DetectorSchema ----
  implicit val jf_det_schemas: RootJsonFormat[DetectorSchemas]               = jsonFormat2(DetectorSchemas)
  implicit val jf_det_schema_create: RootJsonFormat[DetectorSchemaCreateReq] = jsonFormat10(DetectorSchemaCreateReq)
  implicit val jf_det_schema_update: RootJsonFormat[DetectorSchemaUpdateReq] = jsonFormat10(DetectorSchemaUpdateReq)

  // ---- DetectorConfig ----
  implicit val jf_det_configs: RootJsonFormat[DetectorConfigs]               = jsonFormat2(DetectorConfigs)
  implicit val jf_det_config_create: RootJsonFormat[DetectorConfigCreateReq] = jsonFormat8(DetectorConfigCreateReq)
  implicit val jf_det_config_update: RootJsonFormat[DetectorConfigUpdateReq] = jsonFormat8(DetectorConfigUpdateReq)
}
