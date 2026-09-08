package io.hacken.ext.wf

import spray.json._
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.Ingestable

// ============================================================================
// WorkflowSchema
//
// Template for a Workflow DAG, the same way DetectorSchema is a template for an
// individual Node of the DAG. A graph node is represented by a DetectorSchema.
//
// WorkflowSchema has NO workflow functional meaning - it is only used for graph
// mapping to the real implementation logic (separate concern). The implementation
// is responsible for designing the flow of the graph and may not even define some
// steps of the workflow graph.
//
// Relationship: 1 WorkflowSchema -> * WorkflowConfig
// ============================================================================

case class WorkflowSchemaFaq(
  name: String,
  value: String,
)

case class WorkflowSchema(
  id: Int,             // internal unique id
  createdAt: Long,     // timestamp of creation
  updatedAt: Long,     // timestamp of last update
  status: String,      // "ACTIVE, DISABLED, DELETED"
  name: String,        // corresponds to workflowType (e.g. `WorkflowAudit`, `WorkflowPoR`)
  version: String,     // "0.2.7"
  title: String,       // UI title (user defined)
  description: String, // description of the workflow
  author: String,      // author of the workflow
  icon: Option[String],                   // icon of the workflow
  faq: Option[Seq[WorkflowSchemaFaq]],    // FAQ of the workflow
  tags: Seq[String],                      // tags of the workflow

  schema: Option[JsObject],
  uiSchema: Option[JsObject],
  
  // meta contains arbitrary metadata about the workflow (e.g. "tq", "ns").
  // `meta.input` is the default start JSON payload, a String (JSON quoted with `"`).
  // `meta.input_data` is optional `?entity=` CSV (e.g. "detectors,schema") used to query the created
  // WorkflowConfig and store that view JSON as meta.input. Absent/empty (the default) keeps `input`.
  meta: Option[Map[String, Any]] = None, // metadata of the workflow

  graph: WorkflowGraf, // default (template) graph used as a blueprint for WorkflowConfig creation
) extends Ingestable {
  override def getKey: Option[Any] = Some(id)
}

object WorkflowSchema {
  val DEFAULT_ID = 0  // ids start at 0 and are never negative

  /** `meta.input` start payload (a JSON string). */
  def inputOf(meta: Option[Map[String, Any]]): Option[String] =
    meta.flatMap(_.get("input")).collect { case s: String if s.trim.nonEmpty => s }

  /** `meta.input_data` = `?entity=` CSV for a WorkflowConfig query used as start input. Absent/blank = use `input`. */
  def inputDataOf(meta: Option[Map[String, Any]]): Option[String] =
    meta.flatMap(_.get("input_data")).map(_.toString).map(_.trim).filter(_.nonEmpty)

  object Status {
    val ACTIVE   = "ACTIVE"
    val DISABLED = "DISABLED"
    val DELETED  = "DELETED"
    
    val UNSPECIFIED = "UNSPECIFIED"
    val UNKNOWN = "UNKNOWN"
  }

  object Version {
    val DEF_VERSION = "1.0.0"
    val NEW_VERSION = DEF_VERSION
  }

  object Author {
    val DEF_AUTHOR = ""
  }

  /** Convenience builder with sensible defaults (avoid overloading `apply` to keep jsonFormat happy). */
  def of(id: Int, name: String, graph: WorkflowGraf): WorkflowSchema = {
    val now = System.currentTimeMillis()
    WorkflowSchema(
      id = id, 
      createdAt = now, 
      updatedAt = now, 
      status = Status.ACTIVE,
      name = name, 
      version = Version.DEF_VERSION, 
      title = name, 
      description = "", 
      author = "",
      icon = None,
      faq = None,
      tags = Seq(),
      schema = None,
      uiSchema = None,
      graph = graph,
      meta = None,
    )
  }
}

object WorkflowSchemaJson extends JsonCommon {
  import WorkflowGrafJson._
  implicit val jf_wf_faq: RootJsonFormat[WorkflowSchemaFaq] = jsonFormat2(WorkflowSchemaFaq)
  implicit val jf_wf_schema: RootJsonFormat[WorkflowSchema] = jsonFormat16(WorkflowSchema.apply _)
}
