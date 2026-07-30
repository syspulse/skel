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

  // meta contains arbitrary metadata about the workflow (e.g. "namespace", "")
  meta: Option[Map[String, Any]] = None, // metadata of the workflow

  graph: WorkflowGraf, // default (template) graph used as a blueprint for WorkflowConfig creation
) extends Ingestable {
  override def getKey: Option[Any] = Some(id)
}

object WorkflowSchema {
  val DEFAULT_ID = 0  // ids start at 0 and are never negative

  object Status {
    val ACTIVE   = "ACTIVE"
    val DISABLED = "DISABLED"
    val DELETED  = "DELETED"
    
    val UNSPECIFIED = "UNSPECIFIED"
    val UNKNOWN = "UNKNOWN"
  }

  object Version {
    val DEF_VERSION = "1.0.0"
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
      graph = graph,
      meta = None,
    )
  }
}

object WorkflowSchemaJson extends JsonCommon {
  import WorkflowGrafJson._
  implicit val jf_wf_faq: RootJsonFormat[WorkflowSchemaFaq] = jsonFormat2(WorkflowSchemaFaq)
  implicit val jf_wf_schema: RootJsonFormat[WorkflowSchema] = jsonFormat14(WorkflowSchema.apply _)
}
