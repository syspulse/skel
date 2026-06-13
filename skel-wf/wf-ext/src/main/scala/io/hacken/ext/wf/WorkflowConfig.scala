package io.hacken.ext.wf

import spray.json._
import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.Ingestable

// ============================================================================
// WorkflowConfig
//
// Runtime instance of a WorkflowSchema, holding instance config data.
// WorkflowConfig is ALWAYS created from a WorkflowSchema (`sid`). Config data is
// copied from the corresponding WorkflowSchema defaults and may be modified by the
// user at any time.
//
// The Runtime Workflow engine can read and write into WorkflowConfig objects during
// runtime - effectively WorkflowConfig becomes the runtime state of the Workflow
// instance. (Storing runtime state here is NOT mandatory; a real engine may use its
// own state management - same as DetectorConfig.)
//
// Relationship: 1 WorkflowSchema -> * WorkflowConfig
// ============================================================================

case class WorkflowConfig(
  id: Int,             // internal unique id of the config
  sid: Int,            // WorkflowSchema id this config was created from
  createdAt: Long,     // timestamp of creation
  updatedAt: Long,     // timestamp of last update
  status: String,      // "ACTIVE, DISABLED, DELETED"
  name: String,        // custom name set by user (default from WorkflowSchema.name)
  version: String,     // version (default from WorkflowSchema.version)
  title: String,       // UI title
  description: String, // custom user description
  author: String,      // author of the workflow
  icon: Option[String],     // custom icon (default from WorkflowSchema.icon)
  tags: Seq[String],        // custom tags (default from WorkflowSchema.tags)

  graph: WorkflowGraf, // workflow instance graph (graph.cid == Some(this.id))

  oid: Option[String] = None, // owner Id. If specified, must match request oid when present.
  pid: Option[String] = None, // optional project Id
  xid: Option[String] = None, // optional external ID -> Workflow Engine Runtime id
) extends Ingestable {
  override def getKey: Option[Any] = Some(id)
}

object WorkflowConfig {
  val DEFAULT_ID = 0

  /** Build a WorkflowConfig from a WorkflowSchema, copying defaults and re-pointing the graph. */
  def from(id: Int, schema: WorkflowSchema,
           name: Option[String] = None,
           oid: Option[String] = None,
           pid: Option[String] = None,
           xid: Option[String] = None): WorkflowConfig = {
    val now = System.currentTimeMillis()
    WorkflowConfig(
      id = id,
      sid = schema.id,
      createdAt = now,
      updatedAt = now,
      status = WorkflowSchema.Status.ACTIVE,
      name = name.getOrElse(schema.name),
      version = schema.version,
      title = schema.title,
      description = schema.description,
      author = schema.author,
      icon = schema.icon,
      tags = schema.tags,
      graph = schema.graph.copy(cid = Some(id)), // mark graph as a runtime instance
      oid = oid,
      pid = pid,
      xid = xid,
    )
  }
}

object WorkflowConfigJson extends JsonCommon {
  import WorkflowGrafJson._
  implicit val jf_wf_config: RootJsonFormat[WorkflowConfig] = jsonFormat16(WorkflowConfig.apply _)
}
