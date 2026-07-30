package io.syspulse.skel.wf.ext.server

import spray.json.JsObject
import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf, WorkflowSchemaFaq}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorSchemaFaq}

// ============================================================================
// Request / Response protocol for the Workflow `ext` REST API.
//
// `?entity=<csv>` on schema/config retrieval selects which sections come back (default `graf`):
//   - graf     : keep the WorkflowGraf (nodes+links) inline in the config/schema (else stripped)
//   - detector : populate `detectors` with the referenced DetectorConfig (by node cid) [config only]
//   - schema   : populate the DetectorSchema map (config `schemas`, schema `detectors`) by node sid
//   - all      : graf,detector,schema
// e.g. `?entity=detector,schema` or `?entity=all`.
// ============================================================================

final case class WorkflowActionRes(status: String, id: Option[Int] = None)

object WorkflowActionRes {  
  val OK        = "200"
  val NOT_FOUND = "404"
}

// ---------------------------------------------------------------- WorkflowSchema
final case class WorkflowSchemas(
  schemas: Seq[WorkflowSchema],
  total: Long,
  detectors: Option[Map[String, DetectorSchema]] = None,
)
final case class WorkflowSchemaView(
  schema: WorkflowSchema,
  detectors: Option[Map[String, DetectorSchema]] = None,
)
final case class WorkflowSchemaCreateReq(
  name: String,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  author: Option[String] = None,
  icon: Option[String] = None,
  faq: Option[Seq[WorkflowSchemaFaq]] = None,
  tags: Option[Seq[String]] = None,
  graph: Option[WorkflowGraf] = None,
)
final case class WorkflowSchemaUpdateReq(
  name: Option[String] = None,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  status: Option[String] = None,
  icon: Option[String] = None,
  tags: Option[Seq[String]] = None,
  graph: Option[WorkflowGraf] = None,
)
/** Create a WorkflowSchema (and any new DetectorSchema referenced by name) from an Assembly DSL pipeline. */
final case class WorkflowSchemaDslReq(
  pipeline: String,
  wid: Option[Int] = None,
  name: Option[String] = None,
)

// ---------------------------------------------------------------- WorkflowConfig
final case class WorkflowConfigs(
  configs: Seq[WorkflowConfig],
  total: Long,
  detectors: Option[Map[String, DetectorConfig]] = None, // entity=detector -> DetectorConfig by cid
  schemas: Option[Map[String, DetectorSchema]] = None,   // entity=schema   -> DetectorSchema by node sid
)
final case class WorkflowConfigView(
  config: WorkflowConfig,
  detectors: Option[Map[String, DetectorConfig]] = None, // entity=detector -> DetectorConfig by cid
  schemas: Option[Map[String, DetectorSchema]] = None,   // entity=schema   -> DetectorSchema by node sid
)
/** Create a WorkflowConfig from an existing WorkflowSchema (`sid`). */
final case class WorkflowConfigCreateReq(
  sid: Int,
  name: Option[String] = None,
  oid: Option[String] = None,
  pid: Option[String] = None,
  xid: Option[String] = None,
)
final case class WorkflowConfigUpdateReq(
  name: Option[String] = None,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  status: Option[String] = None,
  icon: Option[String] = None,
  tags: Option[Seq[String]] = None,
  graph: Option[WorkflowGraf] = None,
  oid: Option[String] = None,
  pid: Option[String] = None,
  xid: Option[String] = None,
)
/** Assemble a WorkflowConfig (with underlying WorkflowSchema and new Detector* by name) from a DSL pipeline. */
final case class WorkflowConfigDslReq(
  pipeline: String,
  wid: Option[Int] = None,
  name: Option[String] = None,
)

// ---------------------------------------------------------------- DetectorSchema
// Detector entities are referenced by WorkflowNode-s; exposed for the Admin UI tabs
// ("DetectorSchema" / "DetectorConfig") and the editor node palette.
final case class DetectorSchemas(schemas: Seq[DetectorSchema], total: Long)
final case class DetectorSchemaCreateReq(
  name: String,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  author: Option[String] = None,
  icon: Option[String] = None,
  tags: Option[Seq[String]] = None,
  faq: Option[Seq[DetectorSchemaFaq]] = None,
  schema: Option[JsObject] = None,
  uiSchema: Option[JsObject] = None,
)
final case class DetectorSchemaUpdateReq(
  name: Option[String] = None,
  version: Option[String] = None,
  title: Option[String] = None,
  description: Option[String] = None,
  author: Option[String] = None,
  status: Option[String] = None,
  icon: Option[String] = None,
  tags: Option[Seq[String]] = None,
  schema: Option[JsObject] = None,
  uiSchema: Option[JsObject] = None,
)

// ---------------------------------------------------------------- DetectorConfig
final case class DetectorConfigs(configs: Seq[DetectorConfig], total: Long)
/** Create a DetectorConfig; `sid` links it to an existing DetectorSchema (1 schema -> many configs). */
final case class DetectorConfigCreateReq(
  name: String,
  sid: Option[Int] = None,
  source: Option[String] = None,
  status: Option[String] = None,
  tags: Option[Seq[String]] = None,
  config: Option[JsObject] = None,
)
final case class DetectorConfigUpdateReq(
  name: Option[String] = None,
  status: Option[String] = None,
  source: Option[String] = None,
  tags: Option[Seq[String]] = None,
  config: Option[JsObject] = None,
)

// ---------------------------------------------------------------- WorkflowGraf
final case class WorkflowGrafs(grafs: Seq[WorkflowGraf], total: Long)
/** Create a WorkflowGraf. If `id` is omitted the store assigns the next id. */
final case class WorkflowGrafCreateReq(
  id: Option[Int] = None,
  sid: Option[Int] = None,
  cid: Option[Int] = None,
  graph: Option[WorkflowGraf] = None,
)
