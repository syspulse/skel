package io.syspulse.skel.wf.ext.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.ActorContext

import io.syspulse.skel.Command

import spray.json._
import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf, WorkflowNode, WorkflowStatus}
import io.hacken.ext.wf.WorkflowConfigJson._
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract, DetectorConfigSchema}
import io.syspulse.skel.{ErrNotFound, ErrAuthorization}
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL
import io.syspulse.skel.wf.ext.engine.{Engine, TrackMapper, EngineWorkflow, EngineMapper}

object WorkflowRegistry {
  val log = Logger(s"${this}")

  // Report EVERY Store/engine failure that propagates to a reply at ERROR level (it otherwise surfaces
  // only as an opaque HTTP 500 with no server-side trace). Attach with `.andThen(logFail)` right before
  // the terminal `.andThen(logFail).onComplete(replyTo ! _)` (or any reply). No-op on success.
  private val logFail: PartialFunction[Try[Any], Unit] = {
    case Failure(e) => log.error(s"Store operation failed: ${e.getMessage}", e)
  }

  // `entity` is a CSV set of sections to include in Get* responses (for better visibility):
  //   graf     -> the WorkflowGraf (nodes+links) inline in the config/schema
  //   detector -> DetectorConfig map (by node cid) [config only]
  //   schema   -> DetectorSchema map (by node sid)
  //   all      -> graf,detector,schema
  // Empty/absent -> "graf" (the default). When a section is NOT requested the graf is stripped
  // (nodes/links emptied) so the response stays lightweight.
  val ENTITY_GRAF     = "graf"
  val ENTITY_DETECTOR = "detector"
  val ENTITY_SCHEMA   = "schema"
  val ENTITY_ALL      = "all"

  /** Parse a CSV `entity` value into a normalized token set. Accepts singular/plural + a couple of
   *  common typos. Unknown/empty -> the default {graf}. */
  def parseEntities(raw: String): Set[String] = {
    val toks = Option(raw).getOrElse("").split(",").map(_.trim.toLowerCase).filter(_.nonEmpty)
    val expanded: Set[String] = toks.flatMap {
      case ENTITY_ALL                                        => Seq(ENTITY_GRAF, ENTITY_DETECTOR, ENTITY_SCHEMA)
      case "graf" | "graph" | "grafs" | "graphs"             => Seq(ENTITY_GRAF)
      case "detector" | "detectors" | "detectos" | "detecto" => Seq(ENTITY_DETECTOR)
      case "schema" | "schemas" | "schena" | "schemes"       => Seq(ENTITY_SCHEMA)
      case _                                                 => Seq.empty
    }.toSet
    if (expanded.isEmpty) Set(ENTITY_GRAF) else expanded
  }

  /** Strip the heavy graph structure (nodes/links) - used when the `graf` section is NOT requested. */
  private def stripGraf(g: WorkflowGraf): WorkflowGraf = g.copy(nodes = Map.empty, links = Map.empty)

  // ---- WorkflowSchema ----
  final case class GetWorkflowSchemas(from: Option[Long], size: Option[Long], entity: String, replyTo: ActorRef[Try[WorkflowSchemas]]) extends Command
  final case class GetWorkflowSchema(id: Int, entity: String, replyTo: ActorRef[Try[WorkflowSchemaView]]) extends Command
  final case class CreateWorkflowSchema(req: WorkflowSchemaCreateReq, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class CreateWorkflowSchemaDsl(req: WorkflowSchemaDslReq, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class UpdateWorkflowSchema(id: Int, req: WorkflowSchemaUpdateReq, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class DeleteWorkflowSchema(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command
  // Start an Engine (Temporal) execution FROM a WorkflowSchema by id: create a WorkflowConfig from the
  // schema, then start a Workflow with WorkflowType == WorkflowSchema.name and WorkflowId = `wid` (if
  // non-empty) else the new WorkflowConfig.title (or .name if title is empty). taskQueue = request ->
  // config.meta("tq") -> default; input = caller JSON override else the WorkflowConfig JSON. Sets
  // xid = RunId (+ meta.wid), persists, then Resolves live statuses (STARTING while not yet visible).
  final case class StartWorkflowSchema(id: Int, taskQueue: Option[String], input: Option[String], wid: Option[String], ns: Option[String], oid: Option[String], pid: Option[String], author: Option[String], replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command

  // ---- WorkflowConfig ----
  // oid=None skips owner match (admin); pid=None skips project filter. Both are applied in the Store.
  final case class GetWorkflowConfigs(from: Option[Long], size: Option[Long], entity: String, oid: Option[String], pid: Option[String], replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command
  final case class GetWorkflowConfig(id: Int, entity: String, oid: Option[String], pid: Option[String], replyTo: ActorRef[Try[WorkflowConfigView]]) extends Command
  final case class GetWorkflowConfigByXid(xid: String, replyTo: ActorRef[Option[WorkflowConfig]]) extends Command
  final case class GetWorkflowConfigsByOid(oid: String, pid: Option[String], replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command
  // resolve WorkflowConfig(s) (+ all DetectorConfigs) by runtimeId (xid) or workflowId (meta.wid), many ids in one call.
  // typ forces the resolution mode: Some("rid") -> by xid, Some("wid") -> by workflowId, None -> auto-detect (UUID -> rid).
  // oid: filter when fetching from Store (None = admin, no owner filter; Some = WorkflowConfig.oid must match).
  final case class ResolveWorkflowConfigs(ids: Seq[String], typ: Option[String], oid: Option[String], replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command

  val RESOLVE_RID = "rid"  // resolve by runtimeId (WorkflowConfig.xid)
  val RESOLVE_WID = "wid"  // resolve by workflowId (WorkflowConfig.meta.wid / name)
  val RESOLVE_ID  = "id"   // resolve by WorkflowConfig.id (then query the Engine by that config's xid)
  final case class CreateWorkflowConfig(req: WorkflowConfigCreateReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // create a WorkflowConfig from a WorkflowSchema id (composed of DetectorConfig); ids assigned by the store.
  // contractId places the new DetectorConfigs under a contract (default 0 - see Setup0).
  final case class CreateWorkflowConfigFromSchema(sid: Int, contractId: Int, oid: Option[String], replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // bootstrap the default placement (tenant -> project -> contract); all fields parameterized
  final case class Setup0(tenantId: Int, projectId: Int, contractId: Int, name: String, status: String, replyTo: ActorRef[Try[WorkflowActionRes]]) extends Command
  final case class CreateWorkflowConfigDsl(req: WorkflowConfigDslReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // assembly WorkflowConfig from DSL (bracket shorthand accepted) - same as the `assembly` command
  final case class AssemblyWorkflowConfig(req: WorkflowConfigDslReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // assembly + bind to a runtime resolved on the Engine (runtime == None -> fallback xid=fallbackId) - same as `/temporal/assembly`
  final case class AssemblyWorkflowConfigLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // link WorkflowConfig from DSL referencing EXISTING DetectorConfigs by name (latest version) - creates no Detector*
  final case class LinkWorkflowConfig(req: WorkflowConfigDslReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // linkByName + bind to a runtime resolved on the Engine (runtime == None -> fallback xid=fallbackId)
  final case class LinkWorkflowConfigLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class UpdateWorkflowConfig(id: Int, req: WorkflowConfigUpdateReq, oid: Option[String], pid: Option[String], replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class DeleteWorkflowConfig(id: Int, oid: Option[String], pid: Option[String], replyTo: ActorRef[WorkflowActionRes]) extends Command
  // Stop (Temporal terminate) a WorkflowConfig's running Engine workflow -> status TERMINATED (+ persist).
  final case class StopWorkflowConfig(id: Int, reason: Option[String], replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // Cancel (Temporal request-cancel) a WorkflowConfig's running Engine workflow -> status CANCELED (+ persist).
  final case class CancelWorkflowConfig(id: Int, reason: Option[String], replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // Signal (Temporal signal) a WorkflowConfig's running Engine workflow with `name` + optional JSON payload (no status change).
  final case class SignalWorkflowConfig(id: Int, name: String, payload: Option[String], replyTo: ActorRef[Try[WorkflowConfig]]) extends Command

  // ---- WorkflowGraf ----
  final case class GetWorkflowGrafs(from: Option[Long], size: Option[Long], replyTo: ActorRef[Try[WorkflowGrafs]]) extends Command
  final case class GetWorkflowGraf(id: Int, replyTo: ActorRef[Try[WorkflowGraf]]) extends Command
  final case class CreateWorkflowGraf(req: WorkflowGrafCreateReq, replyTo: ActorRef[Try[WorkflowGraf]]) extends Command
  final case class DeleteWorkflowGraf(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

  // ---- DetectorSchema ----
  final case class GetDetectorSchemas(from: Option[Long], size: Option[Long], replyTo: ActorRef[Try[DetectorSchemas]]) extends Command
  final case class GetDetectorSchema(id: Int, replyTo: ActorRef[Try[DetectorSchema]]) extends Command
  final case class CreateDetectorSchema(req: DetectorSchemaCreateReq, replyTo: ActorRef[Try[DetectorSchema]]) extends Command
  final case class UpdateDetectorSchema(id: Int, req: DetectorSchemaUpdateReq, replyTo: ActorRef[Try[DetectorSchema]]) extends Command
  final case class DeleteDetectorSchema(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

  // ---- DetectorConfig ----
  // oid=None skips owner match (admin); pid=None skips project filter. Both are applied in the Store.
  final case class GetDetectorConfigs(from: Option[Long], size: Option[Long], oid: Option[String], pid: Option[String], replyTo: ActorRef[Try[DetectorConfigs]]) extends Command
  final case class GetDetectorConfig(id: Int, oid: Option[String], pid: Option[String], replyTo: ActorRef[Try[DetectorConfig]]) extends Command
  final case class CreateDetectorConfig(req: DetectorConfigCreateReq, replyTo: ActorRef[Try[DetectorConfig]]) extends Command
  final case class UpdateDetectorConfig(id: Int, req: DetectorConfigUpdateReq, oid: Option[String], pid: Option[String], replyTo: ActorRef[Try[DetectorConfig]]) extends Command
  final case class DeleteDetectorConfig(id: Int, oid: Option[String], pid: Option[String], replyTo: ActorRef[WorkflowActionRes]) extends Command

  def apply(store: WorkflowStore, engine: Option[Engine] = None): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.executionContext
      registry(store, engine, context)
    }

  // ---------------------------------------------------------------- view assembly
  // WorkflowSchema references only DetectorSchema (by node sid). `schema`/`all` -> load them; `graf`
  // keeps the graph inline (else it is stripped). The DetectorSchema map is derived from the ORIGINAL
  // graph nodes, so it is unaffected by stripping.
  private def wschemaView(store: WorkflowStore, wschema0: WorkflowSchema, ents: Set[String])(implicit ec: ExecutionContext): Future[WorkflowSchemaView] = {
    val wschema = if (ents(ENTITY_GRAF)) wschema0 else wschema0.copy(graph = stripGraf(wschema0.graph))
    val fSch: Future[Option[Map[String, DetectorSchema]]] =
      if (ents(ENTITY_SCHEMA)) wschemaDschemas(store, Seq(wschema0)).map(Some(_)) else Future.successful(None)
    fSch.map(m => WorkflowSchemaView(wschema, detectors = m))
  }

  /** DetectorSchema map keyed by node sid, for the given graf nodes (schema or config graphs). */
  private def dschemasOf(store: WorkflowStore, sids: Seq[Int])(implicit ec: ExecutionContext): Future[Map[String, DetectorSchema]] = {
    val ids: Seq[Int] = sids.distinct
    Future.sequence(ids.map(id => store.getDSchema(id).map(_.map(id.toString -> _)))).map(_.flatten.toMap)
  }

  private def wschemaDschemas(store: WorkflowStore, wschemas: Seq[WorkflowSchema])(implicit ec: ExecutionContext): Future[Map[String, DetectorSchema]] =
    dschemasOf(store, wschemas.flatMap(_.graph.nodes.values.map(_.sid)))

  private def wconfDschemas(store: WorkflowStore, wconfs: Seq[WorkflowConfig])(implicit ec: ExecutionContext): Future[Map[String, DetectorSchema]] =
    dschemasOf(store, wconfs.flatMap(_.graph.nodes.values.map(_.sid)))

  // WorkflowConfig: `detector` -> DetectorConfig (by cid); `schema` -> DetectorSchema (by sid);
  // `graf` keeps the graph inline (else stripped). Maps derive from the ORIGINAL graph nodes.
  private def wconfView(store: WorkflowStore, wconf0: WorkflowConfig, ents: Set[String])(implicit ec: ExecutionContext): Future[WorkflowConfigView] = {
    val wconf = if (ents(ENTITY_GRAF)) wconf0 else wconf0.copy(graph = stripGraf(wconf0.graph))
    val fDet: Future[Option[Map[String, DetectorConfig]]] =
      if (ents(ENTITY_DETECTOR)) wconfDconfs(store, Seq(wconf0)).map(Some(_)) else Future.successful(None)
    val fSch: Future[Option[Map[String, DetectorSchema]]] =
      if (ents(ENTITY_SCHEMA)) wconfDschemas(store, Seq(wconf0)).map(Some(_)) else Future.successful(None)
    for { dconfsOpt <- fDet; dschemasOpt <- fSch } yield WorkflowConfigView(wconf, detectors = dconfsOpt, schemas = dschemasOpt)
  }

  private def wconfDconfs(store: WorkflowStore, wconfs: Seq[WorkflowConfig])(implicit ec: ExecutionContext): Future[Map[String, DetectorConfig]] = {
    val cids = wconfs.flatMap(_.graph.nodes.values.flatMap(_.cid)).toSet.toSeq
    Future.sequence(cids.map(id => store.getDConf(id).map(_.map(id.toString -> _))))
      .map(_.flatten.toMap)
  }

  /**
   * Right after a start, the new run may not be visible on the Engine yet (visibility lag), so Resolve
   * reports UNRESOLVED. Since the start SUCCEEDED, present (and persist) those UNRESOLVED statuses as
   * STARTING - the WorkflowConfig and its DetectorConfigs. A later Resolve will move them to the real
   * runtime status (RUNNING/...) once visible, or back to UNRESOLVED if the run truly is gone.
   */
  private def markStarting(store: WorkflowStore, wcs: WorkflowConfigs)(implicit ec: ExecutionContext): Future[WorkflowConfigs] = {
    def fix(s: String): String = if (s == WorkflowStatus.UNRESOLVED) WorkflowStatus.STARTING else s
    val wconfs2 = wcs.configs.map(wconf => wconf.copy(status = fix(wconf.status)))
    val dconfs2 = wcs.detectors.map(_.map { case (k, dconf) => k -> dconf.copy(status = fix(dconf.status)) })
    val wconfUp: Seq[Future[_]] = wconfs2.zip(wcs.configs).collect {
      case (wconf, wconf0) if wconf.status != wconf0.status => store.updateWConfStatus(wconf.id, wconf.status)
    }
    val dconfUp: Seq[Future[_]] = dconfs2.getOrElse(Map.empty).toSeq.flatMap { case (k, dconf) =>
      wcs.detectors.flatMap(_.get(k)).filter(_.status != dconf.status).map(_ => store.updateDConfStatus(dconf.id, dconf.status))
    }
    Future.sequence(wconfUp ++ dconfUp).map(_ => wcs.copy(configs = wconfs2, detectors = dconfs2))
  }

  /** WorkflowConfig.meta("wid") as String, if present. */
  private def wconfWid(wconf: WorkflowConfig): Option[String] =
    wconf.meta.flatMap(_.get("wid")).map(_.toString)

  /** Namespace the config's run lives in: meta("ns") (set at start), else None (engine default / all). */
  private def wconfNs(wconf: WorkflowConfig): Option[String] =
    wconf.meta.flatMap(_.get("ns")).map(_.toString).filter(_.nonEmpty)

  /**
   * Resolve WorkflowConfig(s) by runtimeId or workflowId (same detection as assembly-track):
   *   - `rid` -> match WorkflowConfig.xid          (a specific run)
   *   - `wid` -> match WorkflowConfig.meta("wid")  (a WorkflowId), falling back to name
   *   - auto  -> UUID -> `rid`, otherwise `wid`
   * `typ` (Some("rid")|Some("wid")) forces the mode; None auto-detects per id.
   * Returns the matched configs (deduped) plus ALL their DetectorConfigs, with their statuses taken
   * LIVE from the Engine (see `enrichWithEngine`). The resolution MODE that matched each config is
   * kept so the Engine is queried the SAME way (rid -> exact RunId; wid -> latest run).
   */
  private def resolveWconfs(store: WorkflowStore, engine: Option[Engine], ids: Seq[String], typ: Option[String],
                            oid: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowConfigs] =
    store.allWConfs.flatMap { all =>
      def byRid(id: String) = all.filter(_.xid.contains(id))
      def byWid(id: String) = all.filter(wconf => wconfWid(wconf).contains(id) || wconf.name == id)
      def byId(id: String)  = id.toIntOption.map(n => all.filter(_.id == n)).getOrElse(Seq.empty)
      val forced = typ.map(_.trim.toLowerCase)
      // (config, mode) - mode is the criterion used to query the Engine (RESOLVE_RID / RESOLVE_WID).
      // type=id matches the WorkflowConfig by its numeric id, then queries the Engine by that config's xid.
      val foundWithMode: Seq[(WorkflowConfig, String)] = ids.flatMap { id =>
        forced match {
          case Some(RESOLVE_ID) => byId(id).map(wconf => wconf -> RESOLVE_RID)
          case _ =>
            val m = forced.getOrElse(if (TrackMapper.isUuid(id)) RESOLVE_RID else RESOLVE_WID)
            (if (m == RESOLVE_RID) byRid(id) else byWid(id)).map(wconf => wconf -> m)
        }
      }.distinctBy(_._1.id)
      val found = foundWithMode.map(_._1)
      // oid validated at Store fetch (admin: None = allow all; user: JWT oid must own every match)
      if (found.exists(w => !WorkflowStore.owned(w.oid, w.pid, oid, None)))
        Future.failed(new ErrAuthorization(s"WorkflowConfig: ${found.map(_.id).mkString(",")}"))
      else
        wconfDconfs(store, found).flatMap(dconfs => enrichWithEngine(store, engine, foundWithMode, dconfs))
    }

  /**
   * Ask the Engine for the runtime state of a config, STRICTLY by the resolution mode:
   *   - `rid` -> the EXACT run (WorkflowConfig.xid == Temporal RunId). A dead/obsolete RunId returns
   *              None (we NEVER fall back to the workflow's latest run).
   *   - `wid` -> the LATEST run of the WorkflowId (meta.wid, else name).
   */
  private def resolveRuntime(e: Engine, wconf: WorkflowConfig, mode: String)(implicit ec: ExecutionContext): Future[Either[String, EngineWorkflow]] = {
    // query the namespace the run lives in (meta.ns), else None -> the engine searches all namespaces
    val ns = wconfNs(wconf)
    val f = mode match {
      case RESOLVE_WID =>
        wconfWid(wconf).orElse(Option(wconf.name).filter(_.nonEmpty))
          .map(wid => e.getRuntimeByWorkflowId(ns, wid)).getOrElse(Future.successful(None))
      case _ /* RESOLVE_RID */ =>
        wconf.xid.map(rid => e.getRuntime(ns, rid)).getOrElse(Future.successful(None))
    }
    // Right(w) -> found; Left(reason) -> engine returned nothing (Temporal archives non-RUNNING
    // workflows, so a closed/archived run - or one not yet visible - resolves to None), or the query
    // failed. In BOTH Left cases the caller keeps the stored status and records `reason` in meta.err.
    f.map {
      case Some(w) =>
        // log the Engine response so a Resolve is traceable (what the engine actually returned)
        log.info(s"WorkflowConfig(${wconf.id}): RESOLVED: wid=${w.id}, rid=${w.runtimeId}, type=${w.name}, status=${w.status}, ns=${w.namespace}, activities=${w.allActivities.size}")
        Right(w)
      case None    =>
        val reason = s"runtime not found: (mode=${mode}, ns=${ns.getOrElse("*")})"
        // NOT an error, but the caller must still SEE that the config could not be resolved on the engine
        log.warn(s"WorkflowConfig(${wconf.id}): NOT RESOLVED: ${reason}")
        Left(reason)
    }.recover { case ex =>
      // an ENGINE error is NOT the same as "archived/not found": log it at ERROR (with stack) so it is
      // never hidden, and surface the real reason in meta.err (status is kept unchanged by the caller).
      log.error(s"WorkflowConfig(${wconf.id}): FAILED:  mode=${mode}, ns=${ns.getOrElse("*")}: ${ex.getMessage}", ex)
      Left(s"engine query failed: ${ex.getMessage}")
    }
  }

  /**
   * Take the statuses LIVE from the Engine (the stored/cached statuses are never trusted): map each
   * config's runtime state onto its status and its DetectorConfigs' statuses. No-op only when no Engine
   * is configured.
   *
   * ENGINE RETURNS NOTHING (Temporal archives non-RUNNING workflows, so a closed/archived run resolves
   * to None - as does a transient query failure): the WorkflowConfig and its DetectorConfigs KEEP their
   * stored status (NOT flipped to UNRESOLVED) and only `meta.err` is set to the reason.
   *
   * PERSISTENCE (Resolve writes the Engine truth back into the store):
   *   - WorkflowConfig is persisted when its status OR meta.err/meta.result changed (status change alone
   *     uses the cheap status-only UPDATE; a meta change needs the full config write).
   *   - DetectorConfig.status is updated (status-only) whenever it differs.
   */
  private def enrichWithEngine(store: WorkflowStore, engine: Option[Engine], foundWithMode: Seq[(WorkflowConfig, String)], dconfs: Map[String, DetectorConfig])(implicit ec: ExecutionContext): Future[WorkflowConfigs] = {
    val found = foundWithMode.map(_._1)
    
    engine match {
      case None => Future.successful(WorkflowConfigs(found, found.size.toLong, Some(dconfs)))
      case Some(e) =>
        val dconfsByCid: Map[Int, DetectorConfig] = dconfs.map { case (k, v) => k.toInt -> v }
        Future.traverse(foundWithMode) { case (wconf, mode) =>
          resolveRuntime(e, wconf, mode).map {
            case Right(w) =>
              val view = EngineMapper.map(w, Some(wconf), dconfsByCid)
              // cid -> (live status, matched engine activity id)
              val stepInfo = view.steps.flatMap(s => s.cid.map(_ -> (s.status, s.activityId))).toMap
              val base0 = wconf.meta.getOrElse(Map.empty[String, Any])
              // err: carry a failing task's message into meta.err (dropped when the engine reports none)
              val base1 = w.meta.get("err").map(err => base0 + ("err" -> err)).getOrElse(base0 - "err")
              // result: carry the completed run's return value into meta.result (raw JSON string, stored
              // as a String like meta.input). Keep any previously stored result while the run has none yet.
              val base2 = w.meta.get("result").map(r => base1 + ("result" -> r)).getOrElse(base1)
              val meta = Option(base2).filter(_.nonEmpty)
              (wconf.copy(status = view.status, meta = meta), stepInfo)
            case Left(reason) =>
              // engine returned nothing (archived/closed non-RUNNING run, or a query failure): DO NOT
              // change WorkflowConfig/DetectorConfig status - only record the reason in meta.err. Emit
              // each step's CURRENT status so the persistence diff is a no-op for the detectors.
              val meta = Some(wconf.meta.getOrElse(Map.empty[String, Any]) + ("err" -> reason))
              val keep = wconf.graph.nodes.values.flatMap(_.cid)
                .map(cid => cid -> (dconfsByCid.get(cid).map(_.status).getOrElse(WorkflowStatus.UNKNOWN), Option.empty[String]))
                .toMap
              (wconf.copy(meta = meta), keep) // status intentionally UNCHANGED
          }
        }.flatMap { results =>
          val newWconfs   = results.map(_._1)
          val stepInfoAll  = results.flatMap(_._2).toMap    // cid -> (status, activityId)
          val newDconfs = dconfsByCid.map { case (cid, dconf) =>
            val (st, aid) = stepInfoAll.getOrElse(cid, (WorkflowStatus.UNRESOLVED, None))
            // set DetectorConfig.meta.activity_id from the resolved engine activity (dropped when absent)
            val meta = aid.map(a => (dconf.meta.getOrElse(Map.empty) + ("activity_id" -> a)))
              .orElse(dconf.meta.map(_ - "activity_id").filter(_.nonEmpty))
            cid.toString -> dconf.copy(status = st, meta = meta)
          }

          // ---- persist the Engine truth back into the store (only when something changed) ----
          // 1. WorkflowConfig: persist on status change OR when meta.err/meta.result differs. A changed
          //    meta needs the full config write (updateWConfStatus is status-only); a pure status change
          //    keeps the cheap status-only path (targeted UPDATE on the DB store). The archived case
          //    (status unchanged, meta.err newly set) therefore takes the full-write branch.
          def metaKey(wconf: WorkflowConfig, k: String): Option[String] = wconf.meta.flatMap(_.get(k)).map(_.toString)
          val wconfUpdates: Seq[Future[_]] = newWconfs.zip(found).flatMap { case (wconf, wconf0) =>
            if (metaKey(wconf, "result") != metaKey(wconf0, "result") || metaKey(wconf, "err") != metaKey(wconf0, "err")) {
              log.info(s"Resolve: WorkflowConfig(${wconf.id}).status ${wconf0.status} -> ${wconf.status} (meta err/result changed)")
              Some(store.addWConf(wconf.copy(updatedAt = System.currentTimeMillis())))
            } else if (wconf.status != wconf0.status) {
              log.info(s"Resolve: WorkflowConfig(${wconf.id}).status ${wconf0.status} -> ${wconf.status}")
              Some(store.updateWConfStatus(wconf.id, wconf.status))
            } else None
          }
          // 2. DetectorConfig.status - status-only update is implemented for every store (incl. DB)
          val dconfUpdates: Seq[Future[_]] = newDconfs.toSeq.collect {
            case (cidStr, dconf) if dconfs.get(cidStr).exists(_.status != dconf.status) =>
              log.info(s"Resolve: DetectorConfig(${dconf.id}).status ${dconfs(cidStr).status} -> ${dconf.status}")
              store.updateDConfStatus(dconf.id, dconf.status)
          }

          Future.sequence(wconfUpdates ++ dconfUpdates)
            .map(_ => WorkflowConfigs(newWconfs, newWconfs.size.toLong, Some(newDconfs)))
        }
    }
  }

  // ---------------------------------------------------------------- update merge
  private def applyUpdate(wschema: WorkflowSchema, req: WorkflowSchemaUpdateReq): WorkflowSchema =
    wschema.copy(
      updatedAt = System.currentTimeMillis(),
      name = req.name.getOrElse(wschema.name),
      version = req.version.getOrElse(wschema.version),
      title = req.title.getOrElse(wschema.title),
      description = req.description.getOrElse(wschema.description),
      status = req.status.getOrElse(wschema.status),
      icon = req.icon.orElse(wschema.icon),
      tags = req.tags.getOrElse(wschema.tags),
      schema = req.schema.orElse(wschema.schema),
      uiSchema = req.uiSchema.orElse(wschema.uiSchema),
      graph = req.graph.map(WorkflowGraf.sync).getOrElse(wschema.graph),
      meta = req.meta.orElse(wschema.meta),  // allow editing schema metadata
    )

  private def applyUpdate(wconf: WorkflowConfig, req: WorkflowConfigUpdateReq): WorkflowConfig =
    wconf.copy(
      updatedAt = System.currentTimeMillis(),
      name = req.name.getOrElse(wconf.name),
      version = req.version.getOrElse(wconf.version),
      title = req.title.getOrElse(wconf.title),
      description = req.description.getOrElse(wconf.description),
      author = req.author.getOrElse(wconf.author),
      status = req.status.getOrElse(wconf.status),
      icon = req.icon.orElse(wconf.icon),
      tags = req.tags.getOrElse(wconf.tags),
      config = req.config.orElse(wconf.config),
      graph = req.graph.map(WorkflowGraf.sync).getOrElse(wconf.graph),
      oid = req.oid.orElse(wconf.oid),
      pid = req.pid.orElse(wconf.pid),
      xid = req.xid.orElse(wconf.xid),
      meta = req.meta.orElse(wconf.meta),  // allow editing engine metadata (wid/engine/ns/tq/uri/...)
    )

  // ---------------------------------------------------------------- detector builders
  private def dschemaFromReq(id: Int, req: DetectorSchemaCreateReq): DetectorSchema = {
    val now = System.currentTimeMillis()
    DetectorSchema(
      id = id, 
      createdAt = now, 
      updatedAt = now, 
      status = WorkflowSchema.Status.ACTIVE,
      name = req.name, 
      version = req.version.getOrElse(WorkflowSchema.Version.DEF_VERSION),
      title = req.title.getOrElse(req.name), 
      description = req.description.getOrElse(""),
      author = req.author.getOrElse(""), 
      icon = req.icon, 
      faq = req.faq,
      tags = req.tags.getOrElse(Seq()), 
      networkTags = Seq(),
      schema = req.schema, 
      uiSchema = req.uiSchema,
    )
  }

  /** Build a DetectorConfig, linking it to an existing DetectorSchema (`sid`) when provided. */
  private def dconfFromReq(id: Int, req: DetectorConfigCreateReq, dschemaRef: Option[DetectorSchema]): DetectorConfig = {
    val now = System.currentTimeMillis()
    DetectorConfig(
      id = id, 
      createdAt = now, 
      updatedAt = now, 
      status = req.status.getOrElse(WorkflowStatus.UNKNOWN),
      // oid -> contract.tenantId; pid -> contract.projectId (numeric)
      contract = DetectorConfigContract(0, now, now,
        WorkflowStore.dconfProjectId(req.pid), WorkflowStore.dconfTenantId(req.oid),
        None, None, None, None, req.name),
      schema = dschemaRef.map(dschema => DetectorConfigSchema(dschema.id, now, now, dschema.status, dschema.name, dschema.version, None)),
      name = req.name, 
      source = req.source.getOrElse(WorkflowStore.DETECTOR_CONFIG_SOURCE), 
      tags = req.tags.getOrElse(Seq()),
      config = req.config, 
      destinations = Seq(),
    )
  }

  private def applyUpdate(dconf: DetectorConfig, req: DetectorConfigUpdateReq): DetectorConfig = {
    val c = dconf.contract
    val contract = c.copy(
      projectId = req.pid.flatMap(_.toIntOption).getOrElse(c.projectId),
      tenantId = req.oid.flatMap(_.toIntOption).getOrElse(c.tenantId),
    )
    dconf.copy(
      updatedAt = System.currentTimeMillis(),
      status = req.status.getOrElse(dconf.status),
      name = req.name.getOrElse(dconf.name),
      source = req.source.getOrElse(dconf.source),
      tags = req.tags.getOrElse(dconf.tags),
      config = req.config.orElse(dconf.config),
      contract = contract,
      meta = req.meta.orElse(dconf.meta),  // runtime metadata (e.g. activity_id); not in external detector table
    )
  }

  private def applyUpdate(dschema: DetectorSchema, req: DetectorSchemaUpdateReq): DetectorSchema =
    dschema.copy(
      updatedAt = System.currentTimeMillis(),
      name = req.name.getOrElse(dschema.name),
      version = req.version.getOrElse(dschema.version),
      title = req.title.getOrElse(dschema.title),
      description = req.description.getOrElse(dschema.description),
      author = req.author.getOrElse(dschema.author),
      status = req.status.getOrElse(dschema.status),
      icon = req.icon.orElse(dschema.icon),
      tags = req.tags.getOrElse(dschema.tags),
      schema = req.schema.orElse(dschema.schema),
      uiSchema = req.uiSchema.orElse(dschema.uiSchema),
    )

  // ---------------------------------------------------------------- behavior
  private def registry(store: WorkflowStore, engine: Option[Engine], context: ActorContext[Command])(implicit ec: ExecutionContext): Behavior[Command] =
    Behaviors.receiveMessage {

      // -------------------------------------------------- WorkflowSchema
      case GetWorkflowSchemas(from, size, entity, replyTo) =>
        val ents = parseEntities(entity)
        store.listWSchemas(from, size).flatMap { p =>
          val wschemas = if (ents(ENTITY_GRAF)) p.wschemas else p.wschemas.map(wschema => wschema.copy(graph = stripGraf(wschema.graph)))
          if (ents(ENTITY_SCHEMA)) wschemaDschemas(store, p.wschemas).map(m => WorkflowSchemas(wschemas, p.total, Some(m)))
          else Future.successful(WorkflowSchemas(wschemas, p.total, None))
        }.andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowSchema(id, entity, replyTo) =>
        store.getWSchema(id).flatMap(wschema => wschemaView(store, wschema, parseEntities(entity))).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowSchema(req, replyTo) =>
        log.info(s"CreateWorkflowSchema: ${req}")

        store.nextWSchemaId.flatMap { id =>
          val now = System.currentTimeMillis()
          val wschema = WorkflowSchema(
            id = id, 
            createdAt = now, 
            updatedAt = now, 
            status = WorkflowSchema.Status.UNKNOWN,
            name = req.name, 
            version = req.version.getOrElse(WorkflowSchema.Version.DEF_VERSION),
            title = req.title.getOrElse(req.name), 
            description = req.description.getOrElse(""),
            author = req.author.getOrElse(""), 
            icon = req.icon,
            faq = req.faq,
            tags = req.tags.getOrElse(Seq()),
            schema = req.schema,
            uiSchema = req.uiSchema,
            graph = req.graph.map(WorkflowGraf.sync).getOrElse(WorkflowGraf(id = 0, sid = Some(id))),
          )
          store.addWSchema(wschema)
        }.andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowSchemaDsl(req, replyTo) =>
        log.info(s"CreateWorkflowSchemaDsl: pipeline='${req.pipeline}'")
        AssemblyDSL.buildSchema(req.pipeline, store, req.wid, req.name)
          .map(_.schema).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case UpdateWorkflowSchema(id, req, replyTo) =>
        log.info(s"UpdateWorkflowSchema: ${id}: ${req}")

        store.getWSchema(id).map(wschema => applyUpdate(wschema, req)).flatMap(store.addWSchema).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteWorkflowSchema(id, replyTo) =>
        log.info(s"DeleteWorkflowSchema: ${id}")

        store.delWSchema(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- WorkflowConfig
      case GetWorkflowConfigs(from, size, entity, oid, pid, replyTo) =>
        val ents = parseEntities(entity)
        store.listWConfs(from, size, oid, pid).flatMap { p =>
          val wconfs = if (ents(ENTITY_GRAF)) p.wconfs else p.wconfs.map(wconf => wconf.copy(graph = stripGraf(wconf.graph)))
          val fDet: Future[Option[Map[String, DetectorConfig]]] =
            if (ents(ENTITY_DETECTOR)) wconfDconfs(store, p.wconfs).map(Some(_)) else Future.successful(None)
          val fSch: Future[Option[Map[String, DetectorSchema]]] =
            if (ents(ENTITY_SCHEMA)) wconfDschemas(store, p.wconfs).map(Some(_)) else Future.successful(None)
          for { dconfsOpt <- fDet; dschemasOpt <- fSch } yield WorkflowConfigs(wconfs, p.total, detectors = dconfsOpt, schemas = dschemasOpt)
        }.andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowConfig(id, entity, oid, pid, replyTo) =>
        store.getWConf(id, oid, pid).flatMap(wconf => wconfView(store, wconf, parseEntities(entity))).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowConfigByXid(xid, replyTo) =>
        store.findWConfByXid(xid).onComplete {
          case Success(opt) => replyTo ! opt
          case Failure(_)   => replyTo ! None
        }
        Behaviors.same

      case GetWorkflowConfigsByOid(oid, pid, replyTo) =>
        store.listWConfs(oid = Some(oid), pid = pid).map(p => WorkflowConfigs(p.wconfs, p.total, None)).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case ResolveWorkflowConfigs(ids, typ, oid, replyTo) =>
        log.info(s"ResolveWorkflowConfigs: ${engine}/${typ}: oid=${oid}: ${ids}")
        resolveWconfs(store, engine, ids, typ, oid)
          .andThen {
            case Failure(_: ErrAuthorization) => // expected deny — not a Store failure
            case Failure(e)                   => log.error(s"Store operation failed: ${e.getMessage}", e)
            case _                            => ()
          }
          .onComplete(r => {
            log.debug(s"ResolveConfigs: ${engine}/${typ}: oid=${oid}: ${ids}: ${r}")
            replyTo ! r
          })
        Behaviors.same

      case StartWorkflowSchema(id, taskQueue, input, wid, ns, oid, pid, author, replyTo) =>
        log.info(s"StartWorkflowSchema: sid=${id}, tq=${taskQueue}, wid=${wid}, ns=${ns}, oid=${oid}, pid=${pid}, author=${author} => ${engine}")
        engine match {
          case None =>
            replyTo ! Failure(new Exception("Engine not configured"))
          case Some(e) =>
            // Create a WorkflowConfig FROM the schema, then start it on the Engine:
            //   WorkflowType = WorkflowSchema.name (== the created config.name, which defaults to the schema name)
            //   WorkflowId   = `wid` (if non-empty) else new WorkflowConfig.title (or .name if title is empty)
            // When `wid` is provided it is ALSO used as the WorkflowConfig.title (set at creation).
            // taskQueue: request -> config.meta("tq") -> default; input: caller JSON override else config JSON.
            // The caller's start input JSON is recorded into meta.input (stored AS A STRING - JsonMap
            // serializes a String value to a JSON string; an empty body leaves meta.input unset). This
            // is folded into the config BEFORE start(), which preserves meta.* on its single write.
            // Then Resolve pulls the live statuses (STARTING while the run is not yet visible on the Engine).
            val f = for {
              wconf0   <- store.createWConfFromWSchema(id, oid = oid.filter(_.nonEmpty), pid = pid.filter(_.nonEmpty), wid = wid, author = author.filter(_.nonEmpty))
              wconf     = input.filter(_.nonEmpty)
                            .map(in => wconf0.copy(meta = Some(wconf0.meta.getOrElse(Map.empty[String, Any]) + ("input" -> in))))
                            .getOrElse(wconf0)
              tq        = taskQueue.filter(_.nonEmpty)
                            .orElse(wconf.meta.flatMap(_.get("tq")).map(_.toString).filter(_.nonEmpty))
                            .getOrElse(WorkflowAssembly.DEFAULT_TASK_QUEUE)
              payload   = input.filter(_.nonEmpty).orElse(Some(wconf.toJson.compactPrint))
              saved    <- WorkflowAssembly.start(wconf, wconf.name, e, store, tq, payload, ns, wid)
              resolved <- resolveWconfs(store, Some(e), saved.xid.toSeq, Some(RESOLVE_RID))
              started  <- markStarting(store, resolved)
            } yield started
            f.andThen(logFail).onComplete(replyTo ! _)
        }
        Behaviors.same

      case StopWorkflowConfig(id, reason, replyTo) =>
        log.info(s"StopWorkflowConfig: id=${id} reason='${reason.getOrElse("")}'")
        engine match {
          case None    => replyTo ! Failure(new Exception("no Engine configured (start with --engine=temporal://...)"))
          case Some(e) => store.getWConf(id).flatMap(wconf => WorkflowAssembly.stop(wconf, e, store, reason)).andThen(logFail).onComplete(replyTo ! _)
        }
        Behaviors.same

      case CancelWorkflowConfig(id, reason, replyTo) =>
        log.info(s"CancelWorkflowConfig: id=${id} reason='${reason.getOrElse("")}'")
        engine match {
          case None    => replyTo ! Failure(new Exception("no Engine configured (start with --engine=temporal://...)"))
          case Some(e) => store.getWConf(id).flatMap(wconf => WorkflowAssembly.cancel(wconf, e, store, reason)).andThen(logFail).onComplete(replyTo ! _)
        }
        Behaviors.same

      case SignalWorkflowConfig(id, name, payload, replyTo) =>
        log.info(s"SignalWorkflowConfig: id=${id} signal='${name}' payload=${payload.getOrElse("")}")
        engine match {
          case None    => replyTo ! Failure(new Exception("no Engine configured (start with --engine=temporal://...)"))
          case Some(e) => store.getWConf(id).flatMap(wconf => WorkflowAssembly.signal(wconf, e, store, name, payload)).andThen(logFail).onComplete(replyTo ! _)
        }
        Behaviors.same

      case CreateWorkflowConfig(req, replyTo) =>
        log.info(s"CreateWorkflowConfig: ${req}")
        // compose from the schema (with DetectorConfigs); ids are generated by the store; contract 0 (default)
        store.createWConfFromWSchema(req.sid, name = req.name, oid = req.oid, pid = req.pid, xid = req.xid).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowConfigFromSchema(sid, contractId, oid, replyTo) =>
        log.info(s"CreateWorkflowConfigFromSchema: sid=${sid} contractId=${contractId} oid=${oid}")
        store.createWConfFromWSchema(sid, contractId, oid = oid.filter(_.nonEmpty)).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case Setup0(tenantId, projectId, contractId, name, status, replyTo) =>
        log.info(s"Setup0: tenant=${tenantId}, project=${projectId}, contract=${contractId}, name='${name}', status=${status}")
        store.setup0(tenantId, projectId, contractId, name, status)
          .map(_ => WorkflowActionRes(WorkflowActionRes.OK, Some(contractId))).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowConfigDsl(req, replyTo) =>
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case AssemblyWorkflowConfig(req, replyTo) =>
        log.info(s"AssemblyWorkflowConfig: pipeline='${req.pipeline}'")
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case AssemblyWorkflowConfigLinked(req, runtime, fallbackId, replyTo) =>
        log.info(s"AssemblyWorkflowConfigLinked: ${runtime.map(_.id)} / ${fallbackId}: pipeline='${req.pipeline}'")
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name)
          .flatMap(wconf0 => WorkflowAssembly.link(wconf0, runtime, fallbackId, store))
          .andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case LinkWorkflowConfig(req, replyTo) =>
        log.info(s"LinkConfig: pipeline='${req.pipeline}'")
        WorkflowAssembly.linkByName(req.pipeline, store, req.wid, req.name).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case LinkWorkflowConfigLinked(req, runtime, fallbackId, replyTo) =>
        log.info(s"LinkWorkflowConfigLinked: ${runtime.map(_.id)} / ${fallbackId}: pipeline='${req.pipeline}'")
        WorkflowAssembly.linkByName(req.pipeline, store, req.wid, req.name)
          .flatMap(wconf0 => WorkflowAssembly.link(wconf0, runtime, fallbackId, store))
          .andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case UpdateWorkflowConfig(id, req, oid, pid, replyTo) =>
        log.info(s"UpdateWorkflowConfig: ${req} oid=${oid} pid=${pid}")
        store.getWConf(id, oid, pid).map(wconf => applyUpdate(wconf, req)).flatMap(store.addWConf).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteWorkflowConfig(id, oid, pid, replyTo) =>
        log.info(s"DeleteWorkflowConfig: ${id} oid=${oid} pid=${pid}")
        // cascade: delete the WorkflowConfig's DetectorConfig instances + its WorkflowGraf, then the config.
        // each deletion is best-effort (a missing entity does not abort the cascade) and logged at INFO.
        def delQuietly(what: String, f: Future[Int]): Future[Unit] =
          f.map(_ => log.info(s"DeleteWorkflowConfig: ${id}: ${what}"))
            .recover { case e => log.info(s"DeleteWorkflowConfig: ${id}: skip ${what} (${e.getMessage})") }

        val f = store.getWConf(id, oid, pid).flatMap { wconf =>
            val cids = wconf.graph.nodes.values.flatMap(_.cid).toSeq.distinct
            for {
              _ <- Future.sequence(cids.map(cid => delQuietly(s"DetectorConfig(${cid})", store.delDConf(cid))))
              _ <- delQuietly(s"WorkflowGraf(${wconf.graph.id})", store.delGraf(wconf.graph.id))
              _ <- delQuietly(s"WorkflowConfig(${id})", store.delWConf(id))
            } yield WorkflowActionRes(WorkflowActionRes.OK, Some(id))
        }
        f.onComplete {
          case Success(res) => replyTo ! res
          case Failure(e)   => log.info(s"DeleteWorkflowConfig: ${id}: failed (${e.getMessage})"); replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- WorkflowGraf
      case GetWorkflowGrafs(from, size, replyTo) =>
        store.listGrafs(from, size).map(p => WorkflowGrafs(p.grafs, p.total)).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowGraf(id, replyTo) =>
        store.getGraf(id).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowGraf(req, replyTo) =>
        val base = req.graph.getOrElse(WorkflowGraf(id = 0))
        store.nextGrafId.flatMap { nid =>
          val g = base.copy(
            id = req.id.getOrElse(nid),
            sid = req.sid.orElse(base.sid),
            cid = req.cid.orElse(base.cid),
          )
          store.addGraf(g)
        }.andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteWorkflowGraf(id, replyTo) =>
        store.delGraf(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- DetectorSchema
      case GetDetectorSchemas(from, size, replyTo) =>
        store.listDSchemas(from, size).map(p => DetectorSchemas(p.dschemas, p.total)).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case GetDetectorSchema(id, replyTo) =>
        store.getDSchema(id).map {
          case Some(dschema) => dschema
          case None    => throw new ErrNotFound(s"DetectorSchema: ${id}")
        }.andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case CreateDetectorSchema(req, replyTo) =>
        log.info(s"CreateDetectorSchema: ${req.name}")
        store.nextDSchemaId.flatMap(id => store.addDSchema(dschemaFromReq(id, req))).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case UpdateDetectorSchema(id, req, replyTo) =>
        log.info(s"UpdateDetectorSchema: ${id}")
        store.getDSchema(id).map {
          case Some(dschema) => applyUpdate(dschema, req)
          case None    => throw new ErrNotFound(s"DetectorSchema: ${id}")
        }.flatMap(store.addDSchema).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteDetectorSchema(id, replyTo) =>
        store.delDSchema(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- DetectorConfig
      case GetDetectorConfigs(from, size, oid, pid, replyTo) =>
        store.listDConfs(from, size, oid, pid).map(p => DetectorConfigs(p.dconfs, p.total)).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case GetDetectorConfig(id, oid, pid, replyTo) =>
        store.getDConf(id, oid, pid).map {
          case Some(dconf) => dconf
          case None    => throw new ErrNotFound(s"DetectorConfig: ${id}")
        }.andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case CreateDetectorConfig(req, replyTo) =>
        log.info(s"CreateDetectorConfig: ${req.name} oid=${req.oid} pid=${req.pid}")
        val r = for {
          dschemaRef <- req.sid.map(sid => store.getDSchema(sid)).getOrElse(Future.successful(None))
          id         <- store.nextDConfId
          saved      <- store.addDConf(dconfFromReq(id, req, dschemaRef))
        } yield saved
        r.andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case UpdateDetectorConfig(id, req, oid, pid, replyTo) =>
        log.info(s"UpdateDetectorConfig: ${id} oid=${oid} pid=${pid}")
        store.getDConf(id, oid, pid).map {
          case Some(dconf) => applyUpdate(dconf, req)
          case None    => throw new ErrNotFound(s"DetectorConfig: ${id}")
        }.flatMap(store.addDConf).andThen(logFail).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteDetectorConfig(id, oid, pid, replyTo) =>
        store.delDConf(id, oid, pid).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same
    }
}
