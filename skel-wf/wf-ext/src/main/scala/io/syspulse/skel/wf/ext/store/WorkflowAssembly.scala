package io.syspulse.skel.wf.ext.store

import scala.concurrent.{Future, ExecutionContext}

import io.hacken.ext.wf.WorkflowConfig
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, TrackMapper}

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
