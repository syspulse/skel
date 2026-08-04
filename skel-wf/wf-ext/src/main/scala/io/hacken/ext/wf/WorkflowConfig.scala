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
  status: String,      // "ACTIVE, DISABLED, DELETED" (compatibility with Ext), UNKNOWN, FAILED, RUNNING, ...
                       // (it is mapped from the Engine)
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


  // Engine references
  xid: Option[String] = None, // optional external ID -> Workflow Engine Runtime id
  meta: Option[Map[String, Any]] = None, // metadata of the workflow (e.g "namespace", Engine specific)

) extends Ingestable {
  override def getKey: Option[Any] = Some(id)
}

object WorkflowConfig {
  val DEFAULT_ID = 0

  /**
   * Replace `{placeholder}` tokens in schema-derived strings (name / title) so a WorkflowConfig
   * created from the SAME WorkflowSchema gets unique values (used to derive a unique Temporal
   * WorkflowId - see WorkflowAssembly.start):
   *   {id}  -> the NEW WorkflowConfig id
   *   {ts}  -> creation timestamp (epoch millis)
   *   {key} -> meta(key), e.g. "{pid}" == meta.pid; unknown keys resolve to "" (dropped)
   */
  private val PLACEHOLDER = "\\{([A-Za-z0-9_]+)\\}".r
  def substitute(s: String, id: Int, ts: Long, ctx: Map[String, Any]): String =
    if (s == null || s.isEmpty) s
    else PLACEHOLDER.replaceAllIn(s, m => {
      val v = m.group(1) match {
        case "id" => id.toString
        case "ts" => ts.toString
        case k    => ctx.get(k).map(_.toString).getOrElse("")
      }
      java.util.regex.Matcher.quoteReplacement(v)
    })

  /** Build a WorkflowConfig from a WorkflowSchema, copying defaults and re-pointing the graph. */
  def from(id: Int, schema: WorkflowSchema,
           name: Option[String] = None,
           oid: Option[String] = None,
           pid: Option[String] = None,
           xid: Option[String] = None): WorkflowConfig = {
    val now = System.currentTimeMillis()
    // substitution context: schema meta + the config's oid/pid (so "{pid}" resolves to the param or meta)
    val ctx: Map[String, Any] = schema.meta.getOrElse(Map.empty) ++
      pid.map("pid" -> _).toMap ++ oid.map("oid" -> _).toMap
    WorkflowConfig(
      id = id,
      sid = schema.id,
      createdAt = now,
      updatedAt = now,
      status = WorkflowStatus.ACTIVE, // freshly created, not yet resolved against the Engine
      name = substitute(name.getOrElse(schema.name), id, now, ctx),
      version = schema.version,
      title = substitute(schema.title, id, now, ctx),
      description = schema.description,
      author = schema.author,
      icon = schema.icon,
      tags = schema.tags,
      graph = schema.graph.copy(cid = Some(id)), // mark graph as a runtime instance
      oid = oid,
      pid = pid,
      xid = xid,

      meta = schema.meta,
    )
  }
}

object WorkflowConfigJson extends JsonCommon {
  import WorkflowGrafJson._
  implicit val jf_wf_config: RootJsonFormat[WorkflowConfig] = jsonFormat17(WorkflowConfig.apply _)
}
