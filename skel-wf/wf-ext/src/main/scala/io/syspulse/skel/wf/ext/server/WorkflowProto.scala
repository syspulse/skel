package io.syspulse.skel.wf.ext.server

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf, WorkflowSchemaFaq}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}

// ============================================================================
// Request / Response protocol for the Workflow `ext` REST API.
//
// `?detector={id|full}` on schema/config retrieval:
//   - id   (default): nodes carry only sid/cid references; `detectors` is omitted
//   - full          : `detectors` is populated with the referenced DetectorSchema/DetectorConfig
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
  detectors: Option[Map[String, DetectorConfig]] = None,
)
final case class WorkflowConfigView(
  config: WorkflowConfig,
  detectors: Option[Map[String, DetectorConfig]] = None,
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

// ---------------------------------------------------------------- WorkflowGraf
final case class WorkflowGrafs(grafs: Seq[WorkflowGraf], total: Long)
/** Create a WorkflowGraf. If `id` is omitted the store assigns the next id. */
final case class WorkflowGrafCreateReq(
  id: Option[Int] = None,
  sid: Option[Int] = None,
  cid: Option[Int] = None,
  graph: Option[WorkflowGraf] = None,
)
