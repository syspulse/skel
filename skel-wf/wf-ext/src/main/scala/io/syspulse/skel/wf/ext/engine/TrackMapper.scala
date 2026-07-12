package io.syspulse.skel.wf.ext.engine

import scala.concurrent.{Future, ExecutionContext}

// ============================================================================
// TrackMapper
//
// A modular strategy that locates the Engine runtime instance corresponding to a
// WorkflowConfig. Different strategies map the SAME WorkflowConfig to a runtime in
// different ways; the Engine (TemporalEngine) supplies the resolution primitives, so
// new mappers can be added without touching the engine.
//
//   RuntimeIdMapper  - by Temporal RunId (UUID). Pins a SPECIFIC run: xid is fixed.
//   WorkflowIdMapper - by Temporal WorkflowId. Follows the LATEST run: on a restart the
//                      run gets a new RunId, so the mapped WorkflowConfig.xid is reassigned.
//
// In both cases the resolved EngineWorkflow yields:
//   - WorkflowConfig.name / meta.wid  <- EngineWorkflow.id        (Temporal WorkflowId)
//   - WorkflowConfig.xid              <- EngineWorkflow.runtimeId  (Temporal RunId)
// ============================================================================
trait TrackMapper {
  /** Discriminator: "runtimeId" | "workflowId". */
  def kind: String

  /** The identifier value this mapper was built from. */
  def key: String

  /** Resolve the current runtime instance (fully expanded), or None if not found. */
  def resolve(engine: Engine, namespace: Option[String])(implicit ec: ExecutionContext): Future[Option[EngineWorkflow]]
}

object TrackMapper {
  val KIND_RUNTIME_ID  = "runtimeId"
  val KIND_WORKFLOW_ID = "workflowId"

  // A Temporal RunId is a UUID (8-4-4-4-12 hex); a WorkflowId (e.g. PoR-DefaultProject-...) is not.
  private val UUID = "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$".r

  def isUuid(s: String): Boolean = s != null && UUID.pattern.matcher(s).matches()

  /** Auto-detect the mapper from the identifier format: UUID -> RunId, otherwise WorkflowId. */
  def of(id: String): TrackMapper =
    if (isUuid(id)) new RuntimeIdMapper(id) else new WorkflowIdMapper(id)

  /** Build a mapper, forcing the mode when `typ` is given ("rid"/"runtimeId" | "wid"/"workflowId"). */
  def of(id: String, typ: Option[String]): TrackMapper = typ.map(_.trim.toLowerCase) match {
    case Some("rid") | Some("runtimeid")  => new RuntimeIdMapper(id)
    case Some("wid") | Some("workflowid") => new WorkflowIdMapper(id)
    case _                                => of(id)
  }
}

/** Track a specific run by its Temporal RunId (UUID). The run never changes. */
class RuntimeIdMapper(runtimeId: String) extends TrackMapper {
  val kind: String = TrackMapper.KIND_RUNTIME_ID
  val key: String  = runtimeId
  def resolve(engine: Engine, namespace: Option[String])(implicit ec: ExecutionContext): Future[Option[EngineWorkflow]] =
    engine.getRuntime(namespace, runtimeId)
}

/** Track the latest run of a Temporal WorkflowId; RunId (xid) may change on restart. */
class WorkflowIdMapper(workflowId: String) extends TrackMapper {
  val kind: String = TrackMapper.KIND_WORKFLOW_ID
  val key: String  = workflowId
  def resolve(engine: Engine, namespace: Option[String])(implicit ec: ExecutionContext): Future[Option[EngineWorkflow]] =
    engine.getRuntimeByWorkflowId(namespace, workflowId)
}
