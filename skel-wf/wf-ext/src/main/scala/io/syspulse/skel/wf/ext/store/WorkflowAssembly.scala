package io.syspulse.skel.wf.ext.store

import scala.concurrent.{Future, ExecutionContext}

import io.hacken.ext.wf.{WorkflowConfig, WorkflowStatus}
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineStart, TrackMapper}

// ============================================================================
// WorkflowAssembly
//
// Shared assembly/bind logic reused by BOTH the CLI commands (App) and the REST API
// (WorkflowRegistry) - the single source of truth for turning a DSL pipeline into a
// persisted WorkflowConfig and binding it to a runtime instance on the Engine.
//
//   assembly            - DSL pipeline -> persisted WorkflowConfig            (`assembly` / /config/assembly)
//   bind                - WorkflowConfig <- resolved runtime (name/meta.wid/xid)
//   assemblyFromTemporal- assembly + resolve Temporal id via Engine + bind    (`assembly-track` / /temporal/assembly)
//   linkByName          - build WorkflowConfig referencing EXISTING detectors by name (creates none)
//   linkFromTemporal    - linkByName + resolve Temporal id via Engine + bind   (`link` / /temporal/link)
//   link                - bind an already-assembled config to a pre-resolved runtime + persist (API path)
// ============================================================================
object WorkflowAssembly {

  /** Assemble a WorkflowConfig from a DSL pipeline (bracket shorthand accepted), persisted. */
  def assembly(pipeline: String, store: WorkflowStore,
               wid: Option[Int] = None, wname: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowConfig] =
    AssemblyDSL.assembly(AssemblyDSL.normalizePipeline(pipeline), store, wid, wname)
      .map(_.config.getOrElse(throw new Exception("assembly did not produce a WorkflowConfig")))

  /**
   * Like `assembly`, but REFERENCES existing Detector entities instead of creating them: each node
   * is resolved to an existing DetectorConfig by name (latest version) - a missing name fails. Only
   * the WorkflowConfig/WorkflowSchema/WorkflowGraf are created.
   */
  def linkByName(pipeline: String, store: WorkflowStore,
                 wid: Option[Int] = None, wname: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowConfig] =
    AssemblyDSL.linkByName(AssemblyDSL.normalizePipeline(pipeline), store, wid, wname)
      .map(_.config.getOrElse(throw new Exception("link did not produce a WorkflowConfig")))

  /**
   * Bind a WorkflowConfig to a resolved runtime instance:
   *   name / meta.wid <- Temporal WorkflowId (w.id)
   *   xid             <- Temporal RunId (w.runtimeId)  (reassigned on restart)
   */
  def bind(cfg: WorkflowConfig, w: EngineWorkflow): WorkflowConfig = {
    val meta = cfg.meta.getOrElse(Map.empty[String, Any]) + ("wid" -> w.id)
    cfg.copy(name = w.id, xid = Some(w.runtimeId), meta = Some(meta), updatedAt = System.currentTimeMillis())
  }

  /** Whether `cfg` already reflects the binding for runtime `w` (ignoring updatedAt). */
  def isBound(cfg: WorkflowConfig, w: EngineWorkflow): Boolean = {
    val meta = cfg.meta.getOrElse(Map.empty[String, Any]) + ("wid" -> w.id)
    cfg.name == w.id && cfg.xid.contains(w.runtimeId) && cfg.meta.contains(meta)
  }

  val DEFAULT_TASK_QUEUE = "GENERIC_WORKFLOW_QUEUE" // fallback task queue when none is on the request/config

  /**
   * START a new Workflow (Temporal) execution of `cfg`:
   *   - WorkflowType = workflowType             (the WorkflowSchema.name)
   *   - WorkflowId   = `wid` (if non-empty), else cfg.title (or cfg.name if title is empty)
   *   - TaskQueue    = taskQueue                (an INDEPENDENT worker polls it)
   *   - input        = optional JSON payload (caller override, else the WorkflowConfig JSON)
   * Then write the Engine truth back: xid = RunId, meta.wid = WorkflowId (name is left unchanged), persist.
   * The returned config carries the new binding; callers typically Resolve it to pull live statuses.
   */
  def start(cfg: WorkflowConfig, workflowType: String, engine: Engine, store: WorkflowStore, taskQueue: String,
            input: Option[String], ns: Option[String] = None, wid: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowConfig] = {
    val workflowId = wid.map(_.trim).filter(_.nonEmpty)
      .orElse(Option(cfg.title).map(_.trim).filter(_.nonEmpty))
      .getOrElse(cfg.name)
    // pass WorkflowConfig.id ("cid") and WorkflowSchema.id ("sid") as top-level Memo fields so the
    // worker/activity can fetch its configuration from the WorkflowConfig API (GET /config/{cid}).
    // The memo rides alongside the input, not inside it.
    val memo = Map("cid" -> cfg.id.toString, "sid" -> cfg.sid.toString)
    for {
      started <- engine.start(ns, workflowType = workflowType, workflowId = workflowId, taskQueue = taskQueue, input = input, memo = memo)
      // record the runtime binding + where to observe it: engine name, the namespace the run lives in
      // (meta.ns), the task queue it was started on (meta.tq), and a deep-link into the engine panel
      // (meta.url — HTTPS panel when --engine.url is set, else URL derived from the gRPC --engine URI)
      url      = engine.panelUri(workflowType, started.workflowId, started.runtimeId)
      meta     = cfg.meta.getOrElse(Map.empty[String, Any]) +
                   ("wid" -> started.workflowId) + ("engine" -> engine.name) + ("ns" -> started.namespace) +
                   ("tq" -> taskQueue) ++
                   url.map("url" -> _).toMap
      saved   <- store.addConfig(cfg.copy(xid = Some(started.runtimeId), meta = Some(meta), updatedAt = System.currentTimeMillis()))
    } yield saved
  }

  /** WorkflowId a config's run is known by on the Engine: meta.wid, else the config name. */
  private def workflowIdOf(cfg: WorkflowConfig): String =
    cfg.meta.flatMap(_.get("wid")).map(_.toString).filter(_.nonEmpty).getOrElse(cfg.name)

  /** Namespace the config's run lives in: meta.ns (set at start), else None (engine default / all). */
  def nsOf(cfg: WorkflowConfig): Option[String] =
    cfg.meta.flatMap(_.get("ns")).map(_.toString).filter(_.nonEmpty)

  /**
   * STOP (Temporal terminate) the config's running workflow (by workflowId + xid runId), then set
   * WorkflowConfig.status = TERMINATED and persist. `reason` is forwarded to the Engine.
   */
  def stop(cfg: WorkflowConfig, engine: Engine, store: WorkflowStore, reason: Option[String], ns: Option[String] = None)
          (implicit ec: ExecutionContext): Future[WorkflowConfig] =
    for {
      _ <- engine.terminate(ns.orElse(nsOf(cfg)), workflowIdOf(cfg), cfg.xid, reason)
      _ <- store.updateConfigStatus(cfg.id, WorkflowStatus.TERMINATED)
    } yield cfg.copy(status = WorkflowStatus.TERMINATED, updatedAt = System.currentTimeMillis())

  /**
   * CANCEL (Temporal request-cancel) the config's running workflow (by workflowId + xid runId), then
   * set WorkflowConfig.status = CANCELED and persist. `reason` is forwarded to the Engine.
   */
  def cancel(cfg: WorkflowConfig, engine: Engine, store: WorkflowStore, reason: Option[String], ns: Option[String] = None)
            (implicit ec: ExecutionContext): Future[WorkflowConfig] =
    for {
      _ <- engine.cancel(ns.orElse(nsOf(cfg)), workflowIdOf(cfg), cfg.xid, reason)
      _ <- store.updateConfigStatus(cfg.id, WorkflowStatus.CANCELED)
    } yield cfg.copy(status = WorkflowStatus.CANCELED, updatedAt = System.currentTimeMillis())

  /** Bind an already-assembled config to a pre-resolved runtime (or fallback xid=id) and persist. */
  def link(cfg0: WorkflowConfig, runtime: Option[EngineWorkflow], fallbackId: String, store: WorkflowStore)
          (implicit ec: ExecutionContext): Future[WorkflowConfig] = {
    val cfg = runtime.map(w => bind(cfg0, w))
      .getOrElse(cfg0.copy(xid = Some(fallbackId), updatedAt = System.currentTimeMillis()))
    store.addConfig(cfg)
  }

  /**
   * Assemble from DSL, resolve the Temporal id (runtimeId or workflowId) via the Engine, bind, persist.
   * If the runtime can't be resolved (engine down / not found) it falls back to xid = id.
   */
  def assemblyFromTemporal(id: String, pipeline: String, engine: Engine, store: WorkflowStore,
                           ns: Option[String] = None, wid: Option[Int] = None, wname: Option[String] = None)
                          (implicit ec: ExecutionContext): Future[WorkflowConfig] = {
    val mapper = TrackMapper.of(id)
    for {
      cfg0  <- assembly(pipeline, store, wid, wname)
      wOpt  <- mapper.resolve(engine, ns).recover { case _ => None }
      saved <- link(cfg0, wOpt, id, store)
    } yield saved
  }

  /**
   * Like `assemblyFromTemporal`, but REFERENCES existing detectors instead of creating them
   * (`linkByName`): each pipeline node resolves to an existing DetectorConfig by name (latest
   * version) - a missing name fails. Resolve the Temporal id via the Engine, bind, persist.
   */
  def linkFromTemporal(id: String, pipeline: String, engine: Engine, store: WorkflowStore,
                       ns: Option[String] = None, wid: Option[Int] = None, wname: Option[String] = None)
                      (implicit ec: ExecutionContext): Future[WorkflowConfig] = {
    val mapper = TrackMapper.of(id)
    for {
      cfg0  <- linkByName(pipeline, store, wid, wname)
      wOpt  <- mapper.resolve(engine, ns).recover { case _ => None }
      saved <- link(cfg0, wOpt, id, store)
    } yield saved
  }
}
