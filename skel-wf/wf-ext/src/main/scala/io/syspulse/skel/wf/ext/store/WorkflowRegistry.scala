package io.syspulse.skel.wf.ext.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.scaladsl.ActorContext

import io.syspulse.skel.Command

import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf, WorkflowNode}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract, DetectorConfigSchema}
import io.syspulse.skel.ErrNotFound
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL
import io.syspulse.skel.wf.ext.engine.{Engine, TrackMapper, EngineWorkflow, EngineMapper, EngineStatus}

object WorkflowRegistry {
  val log = Logger(s"${this}")

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

  // ---- WorkflowConfig ----
  final case class GetWorkflowConfigs(from: Option[Long], size: Option[Long], entity: String, replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command
  final case class GetWorkflowConfig(id: Int, entity: String, replyTo: ActorRef[Try[WorkflowConfigView]]) extends Command
  final case class GetWorkflowConfigByXid(xid: String, replyTo: ActorRef[Option[WorkflowConfig]]) extends Command
  final case class GetWorkflowConfigsByOid(oid: String, replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command
  // resolve WorkflowConfig(s) (+ all DetectorConfigs) by runtimeId (xid) or workflowId (meta.wid), many ids in one call.
  // typ forces the resolution mode: Some("rid") -> by xid, Some("wid") -> by workflowId, None -> auto-detect (UUID -> rid).
  final case class ResolveWorkflowConfigs(ids: Seq[String], typ: Option[String], replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command

  val RESOLVE_RID = "rid"  // resolve by runtimeId (WorkflowConfig.xid)
  val RESOLVE_WID = "wid"  // resolve by workflowId (WorkflowConfig.meta.wid / name)
  final case class CreateWorkflowConfig(req: WorkflowConfigCreateReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // create a WorkflowConfig from a WorkflowSchema id (composed of DetectorConfig); ids assigned by the store.
  // contractId places the new DetectorConfigs under a contract (default 0 - see Setup0).
  final case class CreateWorkflowConfigFromSchema(sid: Int, contractId: Int, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
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
  final case class UpdateWorkflowConfig(id: Int, req: WorkflowConfigUpdateReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class DeleteWorkflowConfig(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

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
  final case class GetDetectorConfigs(from: Option[Long], size: Option[Long], replyTo: ActorRef[Try[DetectorConfigs]]) extends Command
  final case class GetDetectorConfig(id: Int, replyTo: ActorRef[Try[DetectorConfig]]) extends Command
  final case class CreateDetectorConfig(req: DetectorConfigCreateReq, replyTo: ActorRef[Try[DetectorConfig]]) extends Command
  final case class UpdateDetectorConfig(id: Int, req: DetectorConfigUpdateReq, replyTo: ActorRef[Try[DetectorConfig]]) extends Command
  final case class DeleteDetectorConfig(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

  def apply(store: WorkflowStore, engine: Option[Engine] = None): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.executionContext
      registry(store, engine, context)
    }

  // ---------------------------------------------------------------- view assembly
  // WorkflowSchema references only DetectorSchema (by node sid). `schema`/`all` -> load them; `graf`
  // keeps the graph inline (else it is stripped). The DetectorSchema map is derived from the ORIGINAL
  // graph nodes, so it is unaffected by stripping.
  private def schemaView(store: WorkflowStore, s: WorkflowSchema, ents: Set[String])(implicit ec: ExecutionContext): Future[WorkflowSchemaView] = {
    val schema = if (ents(ENTITY_GRAF)) s else s.copy(graph = stripGraf(s.graph))
    val fSch: Future[Option[Map[String, DetectorSchema]]] =
      if (ents(ENTITY_SCHEMA)) schemaDetectors(store, Seq(s)).map(Some(_)) else Future.successful(None)
    fSch.map(m => WorkflowSchemaView(schema, detectors = m))
  }

  /** DetectorSchema map keyed by node sid, for the given graf nodes (schema or config graphs). */
  private def detectorSchemasOf(store: WorkflowStore, sids: Seq[Int])(implicit ec: ExecutionContext): Future[Map[String, DetectorSchema]] = {
    val ids: Seq[Int] = sids.distinct
    Future.sequence(ids.map(id => store.getDetectorSchema(id).map(_.map(id.toString -> _)))).map(_.flatten.toMap)
  }

  private def schemaDetectors(store: WorkflowStore, ss: Seq[WorkflowSchema])(implicit ec: ExecutionContext): Future[Map[String, DetectorSchema]] =
    detectorSchemasOf(store, ss.flatMap(_.graph.nodes.values.map(_.sid)))

  private def configSchemas(store: WorkflowStore, cs: Seq[WorkflowConfig])(implicit ec: ExecutionContext): Future[Map[String, DetectorSchema]] =
    detectorSchemasOf(store, cs.flatMap(_.graph.nodes.values.map(_.sid)))

  // WorkflowConfig: `detector` -> DetectorConfig (by cid); `schema` -> DetectorSchema (by sid);
  // `graf` keeps the graph inline (else stripped). Maps derive from the ORIGINAL graph nodes.
  private def configView(store: WorkflowStore, c: WorkflowConfig, ents: Set[String])(implicit ec: ExecutionContext): Future[WorkflowConfigView] = {
    val cfg = if (ents(ENTITY_GRAF)) c else c.copy(graph = stripGraf(c.graph))
    val fDet: Future[Option[Map[String, DetectorConfig]]] =
      if (ents(ENTITY_DETECTOR)) configDetectors(store, Seq(c)).map(Some(_)) else Future.successful(None)
    val fSch: Future[Option[Map[String, DetectorSchema]]] =
      if (ents(ENTITY_SCHEMA)) configSchemas(store, Seq(c)).map(Some(_)) else Future.successful(None)
    for { d <- fDet; s <- fSch } yield WorkflowConfigView(cfg, detectors = d, schemas = s)
  }

  private def configDetectors(store: WorkflowStore, cs: Seq[WorkflowConfig])(implicit ec: ExecutionContext): Future[Map[String, DetectorConfig]] = {
    val cids = cs.flatMap(_.graph.nodes.values.flatMap(_.cid)).toSet.toSeq
    Future.sequence(cids.map(id => store.getDetectorConfig(id).map(_.map(id.toString -> _))))
      .map(_.flatten.toMap)
  }

  /** WorkflowConfig.meta("wid") as String, if present. */
  private def configWid(c: WorkflowConfig): Option[String] =
    c.meta.flatMap(_.get("wid")).map(_.toString)

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
  private def resolveConfigs(store: WorkflowStore, engine: Option[Engine], ids: Seq[String], typ: Option[String])(implicit ec: ExecutionContext): Future[WorkflowConfigs] =
    store.allConfigs.flatMap { all =>
      def byRid(id: String) = all.filter(_.xid.contains(id))
      def byWid(id: String) = all.filter(c => configWid(c).contains(id) || c.name == id)
      val forced = typ.map(_.trim.toLowerCase)
      // (config, mode) - mode is the criterion that matched it (RESOLVE_RID / RESOLVE_WID)
      val foundWithMode: Seq[(WorkflowConfig, String)] = ids.flatMap { id =>
        val m = forced.getOrElse(if (TrackMapper.isUuid(id)) RESOLVE_RID else RESOLVE_WID)
        (if (m == RESOLVE_RID) byRid(id) else byWid(id)).map(c => c -> m)
      }.distinctBy(_._1.id)
      val found = foundWithMode.map(_._1)
      configDetectors(store, found).flatMap(dets => enrichWithEngine(store, engine, foundWithMode, dets))
    }

  /**
   * Ask the Engine for the runtime state of a config, STRICTLY by the resolution mode:
   *   - `rid` -> the EXACT run (WorkflowConfig.xid == Temporal RunId). A dead/obsolete RunId returns
   *              None (we NEVER fall back to the workflow's latest run).
   *   - `wid` -> the LATEST run of the WorkflowId (meta.wid, else name).
   */
  private def resolveRuntime(e: Engine, c: WorkflowConfig, mode: String)(implicit ec: ExecutionContext): Future[Option[EngineWorkflow]] = {
    val f = mode match {
      case RESOLVE_WID =>
        configWid(c).orElse(Option(c.name).filter(_.nonEmpty))
          .map(wid => e.getRuntimeByWorkflowId(None, wid)).getOrElse(Future.successful(None))
      case _ /* RESOLVE_RID */ =>
        c.xid.map(rid => e.getRuntime(None, rid)).getOrElse(Future.successful(None))
    }
    // engine failures are logged at the source (TemporalEngine.call); add request-level context here.
    // NOTE: a failure degrades to None -> the config resolves as UNRESOLVED (see enrichWithEngine).
    f.recover { case ex =>
      log.warn(s"resolveRuntime: engine query failed for WorkflowConfig(${c.id}) mode=${mode} -> UNRESOLVED: ${ex.getMessage}")
      None
    }
  }

  /**
   * Take the statuses LIVE from the Engine (the stored/cached statuses are never trusted): map each
   * config's runtime state onto its status and its DetectorConfigs' statuses. When the runtime cannot
   * be resolved on the Engine (obsolete/removed id), the WorkflowConfig AND all its DetectorConfigs
   * are marked `UNRESOLVED`. No-op only when no Engine is configured.
   *
   * PERSISTENCE (Resolve writes the Engine truth back into the store):
   *   - WorkflowConfig.status is updated in the store whenever it differs from the Engine value (any store).
   *   - DetectorConfig.status is updated only for stores that allow it (`canUpdateDetectorConfig` -
   *     WorkflowStoreMem / WorkflowStoreDir). The DB store is NOT written for now.
   */
  private def enrichWithEngine(store: WorkflowStore, engine: Option[Engine], foundWithMode: Seq[(WorkflowConfig, String)], dets: Map[String, DetectorConfig])(implicit ec: ExecutionContext): Future[WorkflowConfigs] = {
    val found = foundWithMode.map(_._1)
    engine match {
      case None => Future.successful(WorkflowConfigs(found, found.size.toLong, Some(dets)))
      case Some(e) =>
        val detectorsInt: Map[Int, DetectorConfig] = dets.map { case (k, v) => k.toInt -> v }
        Future.traverse(foundWithMode) { case (c, mode) =>
          resolveRuntime(e, c, mode).map {
            case Some(w) =>
              val view = EngineMapper.map(w, Some(c), detectorsInt)
              // cid -> (live status, matched engine activity id)
              val stepInfo = view.steps.flatMap(s => s.cid.map(_ -> (s.status, s.activityId))).toMap
              (c.copy(status = view.status), stepInfo)
            case None =>
              // runtime not present on the Engine -> the whole config (and every step) is UNRESOLVED
              val cids = c.graph.nodes.values.flatMap(_.cid).toSeq
              (c.copy(status = EngineStatus.UNRESOLVED), cids.map(_ -> (EngineStatus.UNRESOLVED, Option.empty[String])).toMap)
          }
        }.flatMap { results =>
          val newConfigs   = results.map(_._1)
          val stepInfoAll  = results.flatMap(_._2).toMap    // cid -> (status, activityId)
          val newDetectors = detectorsInt.map { case (cid, dc) =>
            val (st, aid) = stepInfoAll.getOrElse(cid, (EngineStatus.UNRESOLVED, None))
            // set DetectorConfig.meta.activity_id from the resolved engine activity (dropped when absent)
            val meta = aid.map(a => (dc.meta.getOrElse(Map.empty) + ("activity_id" -> a)))
              .orElse(dc.meta.map(_ - "activity_id").filter(_.nonEmpty))
            cid.toString -> dc.copy(status = st, meta = meta)
          }

          // ---- persist the Engine truth back into the store (status-only, only when changed) ----
          // 1. WorkflowConfig.status (all stores)
          val cfgUpdates: Seq[Future[_]] = newConfigs.zip(found).collect {
            case (nc, oc) if nc.status != oc.status =>
              log.info(s"Resolve: WorkflowConfig(${nc.id}).status ${oc.status} -> ${nc.status}")
              store.updateConfigStatus(nc.id, nc.status)
          }
          // 2. DetectorConfig.status - status-only update is implemented for every store (incl. DB)
          val detUpdates: Seq[Future[_]] = newDetectors.toSeq.collect {
            case (cidStr, nd) if dets.get(cidStr).exists(_.status != nd.status) =>
              log.info(s"Resolve: DetectorConfig(${nd.id}).status ${dets(cidStr).status} -> ${nd.status}")
              store.updateDetectorConfigStatus(nd.id, nd.status)
          }

          Future.sequence(cfgUpdates ++ detUpdates)
            .map(_ => WorkflowConfigs(newConfigs, newConfigs.size.toLong, Some(newDetectors)))
        }
    }
  }

  // ---------------------------------------------------------------- update merge
  private def applyUpdate(s: WorkflowSchema, req: WorkflowSchemaUpdateReq): WorkflowSchema =
    s.copy(
      updatedAt = System.currentTimeMillis(),
      name = req.name.getOrElse(s.name),
      version = req.version.getOrElse(s.version),
      title = req.title.getOrElse(s.title),
      description = req.description.getOrElse(s.description),
      status = req.status.getOrElse(s.status),
      icon = req.icon.orElse(s.icon),
      tags = req.tags.getOrElse(s.tags),
      graph = req.graph.map(WorkflowGraf.sync).getOrElse(s.graph),
    )

  private def applyUpdate(c: WorkflowConfig, req: WorkflowConfigUpdateReq): WorkflowConfig =
    c.copy(
      updatedAt = System.currentTimeMillis(),
      name = req.name.getOrElse(c.name),
      version = req.version.getOrElse(c.version),
      title = req.title.getOrElse(c.title),
      description = req.description.getOrElse(c.description),
      status = req.status.getOrElse(c.status),
      icon = req.icon.orElse(c.icon),
      tags = req.tags.getOrElse(c.tags),
      graph = req.graph.map(WorkflowGraf.sync).getOrElse(c.graph),
      oid = req.oid.orElse(c.oid),
      pid = req.pid.orElse(c.pid),
      xid = req.xid.orElse(c.xid),
    )

  // ---------------------------------------------------------------- detector builders
  private def detectorSchemaFromReq(id: Int, req: DetectorSchemaCreateReq): DetectorSchema = {
    val now = System.currentTimeMillis()
    DetectorSchema(
      id = id, createdAt = now, updatedAt = now, status = WorkflowSchema.Status.ACTIVE,
      name = req.name, version = req.version.getOrElse(WorkflowSchema.Version.DEF_VERSION),
      title = req.title.getOrElse(req.name), description = req.description.getOrElse(""),
      author = req.author.getOrElse(""), icon = req.icon, faq = req.faq,
      tags = req.tags.getOrElse(Seq()), networkTags = Seq(),
      schema = req.schema, uiSchema = req.uiSchema,
    )
  }

  /** Build a DetectorConfig, linking it to an existing DetectorSchema (`sid`) when provided. */
  private def detectorConfigFromReq(id: Int, req: DetectorConfigCreateReq, schemaRef: Option[DetectorSchema]): DetectorConfig = {
    val now = System.currentTimeMillis()
    DetectorConfig(
      id = id, createdAt = now, updatedAt = now, status = req.status.getOrElse(WorkflowSchema.Status.ACTIVE),
      contract = DetectorConfigContract(0, now, now, 0, 0, None, None, None, None, req.name),
      schema = schemaRef.map(ds => DetectorConfigSchema(ds.id, now, now, ds.status, ds.name, ds.version, None)),
      name = req.name, source = req.source.getOrElse(""), tags = req.tags.getOrElse(Seq()),
      config = req.config, destinations = Seq(),
    )
  }

  private def applyUpdate(c: DetectorConfig, req: DetectorConfigUpdateReq): DetectorConfig =
    c.copy(
      updatedAt = System.currentTimeMillis(),
      status = req.status.getOrElse(c.status),
      name = req.name.getOrElse(c.name),
      source = req.source.getOrElse(c.source),
      tags = req.tags.getOrElse(c.tags),
      config = req.config.orElse(c.config),
    )

  private def applyUpdate(d: DetectorSchema, req: DetectorSchemaUpdateReq): DetectorSchema =
    d.copy(
      updatedAt = System.currentTimeMillis(),
      name = req.name.getOrElse(d.name),
      version = req.version.getOrElse(d.version),
      title = req.title.getOrElse(d.title),
      description = req.description.getOrElse(d.description),
      author = req.author.getOrElse(d.author),
      status = req.status.getOrElse(d.status),
      icon = req.icon.orElse(d.icon),
      tags = req.tags.getOrElse(d.tags),
      schema = req.schema.orElse(d.schema),
      uiSchema = req.uiSchema.orElse(d.uiSchema),
    )

  // ---------------------------------------------------------------- behavior
  private def registry(store: WorkflowStore, engine: Option[Engine], context: ActorContext[Command])(implicit ec: ExecutionContext): Behavior[Command] =
    Behaviors.receiveMessage {

      // -------------------------------------------------- WorkflowSchema
      case GetWorkflowSchemas(from, size, entity, replyTo) =>
        val ents = parseEntities(entity)
        store.listSchemas(from, size).flatMap { p =>
          val schemas = if (ents(ENTITY_GRAF)) p.schemas else p.schemas.map(s => s.copy(graph = stripGraf(s.graph)))
          if (ents(ENTITY_SCHEMA)) schemaDetectors(store, p.schemas).map(m => WorkflowSchemas(schemas, p.total, Some(m)))
          else Future.successful(WorkflowSchemas(schemas, p.total, None))
        }.onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowSchema(id, entity, replyTo) =>
        store.getSchema(id).flatMap(s => schemaView(store, s, parseEntities(entity))).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowSchema(req, replyTo) =>
        log.info(s"CreateWorkflowSchema: ${req}")

        store.nextSchemaId.flatMap { id =>
          val now = System.currentTimeMillis()
          val s = WorkflowSchema(
            id = id, createdAt = now, updatedAt = now, status = WorkflowSchema.Status.ACTIVE,
            name = req.name, version = req.version.getOrElse(WorkflowSchema.Version.DEF_VERSION),
            title = req.title.getOrElse(req.name), description = req.description.getOrElse(""),
            author = req.author.getOrElse(""), icon = req.icon, faq = req.faq,
            tags = req.tags.getOrElse(Seq()),
            graph = req.graph.map(WorkflowGraf.sync).getOrElse(WorkflowGraf(id = 0, sid = Some(id))),
          )
          store.addSchema(s)
        }.onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowSchemaDsl(req, replyTo) =>
        log.info(s"CreateWorkflowSchemaDsl: pipeline='${req.pipeline}'")
        AssemblyDSL.buildSchema(req.pipeline, store, req.wid, req.name)
          .map(_.schema).onComplete(replyTo ! _)
        Behaviors.same

      case UpdateWorkflowSchema(id, req, replyTo) =>
        log.info(s"UpdateWorkflowSchema: ${id}: ${req}")

        store.getSchema(id).map(s => applyUpdate(s, req)).flatMap(store.addSchema).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteWorkflowSchema(id, replyTo) =>
        log.info(s"DeleteWorkflowSchema: ${id}")

        store.delSchema(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- WorkflowConfig
      case GetWorkflowConfigs(from, size, entity, replyTo) =>
        val ents = parseEntities(entity)
        store.listConfigs(from, size).flatMap { p =>
          val configs = if (ents(ENTITY_GRAF)) p.configs else p.configs.map(c => c.copy(graph = stripGraf(c.graph)))
          val fDet: Future[Option[Map[String, DetectorConfig]]] =
            if (ents(ENTITY_DETECTOR)) configDetectors(store, p.configs).map(Some(_)) else Future.successful(None)
          val fSch: Future[Option[Map[String, DetectorSchema]]] =
            if (ents(ENTITY_SCHEMA)) configSchemas(store, p.configs).map(Some(_)) else Future.successful(None)
          for { d <- fDet; s <- fSch } yield WorkflowConfigs(configs, p.total, detectors = d, schemas = s)
        }.onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowConfig(id, entity, replyTo) =>
        store.getConfig(id).flatMap(c => configView(store, c, parseEntities(entity))).onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowConfigByXid(xid, replyTo) =>
        store.findConfigByXid(xid).onComplete {
          case Success(opt) => replyTo ! opt
          case Failure(_)   => replyTo ! None
        }
        Behaviors.same

      case GetWorkflowConfigsByOid(oid, replyTo) =>
        store.findConfigByOid(oid).map(cs => WorkflowConfigs(cs, cs.size.toLong, None)).onComplete(replyTo ! _)
        Behaviors.same

      case ResolveWorkflowConfigs(ids, typ, replyTo) =>
        log.info(s"ResolveWorkflowConfigs: ${engine}/${typ}: ${ids}")
        resolveConfigs(store, engine, ids, typ)
          .onComplete(r => {
            log.debug(s"ResolveConfigs: ${engine}/${typ}: ${ids}: ${r}")
            replyTo ! r
          })
        Behaviors.same

      case CreateWorkflowConfig(req, replyTo) =>
        log.info(s"CreateWorkflowConfig: ${req}")
        // compose from the schema (with DetectorConfigs); ids are generated by the store; contract 0 (default)
        store.createConfigFromSchema(req.sid, name = req.name, oid = req.oid, pid = req.pid, xid = req.xid).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowConfigFromSchema(sid, contractId, replyTo) =>
        log.info(s"CreateWorkflowConfigFromSchema: sid=${sid} contractId=${contractId}")
        store.createConfigFromSchema(sid, contractId).onComplete(replyTo ! _)
        Behaviors.same

      case Setup0(tenantId, projectId, contractId, name, status, replyTo) =>
        log.info(s"Setup0: tenant=${tenantId}, project=${projectId}, contract=${contractId}, name='${name}', status=${status}")
        store.setup0(tenantId, projectId, contractId, name, status)
          .map(_ => WorkflowActionRes(WorkflowActionRes.OK, Some(contractId))).onComplete(replyTo ! _)
        Behaviors.same

      case CreateWorkflowConfigDsl(req, replyTo) =>
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name).onComplete(replyTo ! _)
        Behaviors.same

      case AssemblyWorkflowConfig(req, replyTo) =>
        log.info(s"AssemblyWorkflowConfig: pipeline='${req.pipeline}'")
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name).onComplete(replyTo ! _)
        Behaviors.same

      case AssemblyWorkflowConfigLinked(req, runtime, fallbackId, replyTo) =>
        log.info(s"AssemblyWorkflowConfigLinked: ${runtime.map(_.id)} / ${fallbackId}: pipeline='${req.pipeline}'")
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name)
          .flatMap(cfg0 => WorkflowAssembly.link(cfg0, runtime, fallbackId, store))
          .onComplete(replyTo ! _)
        Behaviors.same

      case LinkWorkflowConfig(req, replyTo) =>
        log.info(s"LinkConfig: pipeline='${req.pipeline}'")
        WorkflowAssembly.linkByName(req.pipeline, store, req.wid, req.name).onComplete(replyTo ! _)
        Behaviors.same

      case LinkWorkflowConfigLinked(req, runtime, fallbackId, replyTo) =>
        log.info(s"LinkWorkflowConfigLinked: ${runtime.map(_.id)} / ${fallbackId}: pipeline='${req.pipeline}'")
        WorkflowAssembly.linkByName(req.pipeline, store, req.wid, req.name)
          .flatMap(cfg0 => WorkflowAssembly.link(cfg0, runtime, fallbackId, store))
          .onComplete(replyTo ! _)
        Behaviors.same

      case UpdateWorkflowConfig(id, req, replyTo) =>
        log.info(s"UpdateWorkflowConfig: ${req}")

        store.getConfig(id).map(c => applyUpdate(c, req)).flatMap(store.addConfig).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteWorkflowConfig(id, replyTo) =>
        log.info(s"DeleteWorkflowConfig: ${id}")
        // cascade: delete the WorkflowConfig's DetectorConfig instances + its WorkflowGraf, then the config.
        // each deletion is best-effort (a missing entity does not abort the cascade) and logged at INFO.
        def delQuietly(what: String, f: Future[Int]): Future[Unit] =
          f.map(_ => log.info(s"DeleteWorkflowConfig: ${id}: ${what}"))
            .recover { case e => log.info(s"DeleteWorkflowConfig: ${id}: skip ${what} (${e.getMessage})") }

        val f = store.getConfigOpt(id).flatMap {
          case None => Future.successful(WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id)))
          case Some(c) =>
            val cids = c.graph.nodes.values.flatMap(_.cid).toSeq.distinct
            for {
              _ <- Future.sequence(cids.map(cid => delQuietly(s"DetectorConfig(${cid})", store.delDetectorConfig(cid))))
              _ <- delQuietly(s"WorkflowGraf(${c.graph.id})", store.delGraf(c.graph.id))
              _ <- delQuietly(s"WorkflowConfig(${id})", store.delConfig(id))
            } yield WorkflowActionRes(WorkflowActionRes.OK, Some(id))
        }
        f.onComplete {
          case Success(res) => replyTo ! res
          case Failure(e)   => log.info(s"DeleteWorkflowConfig: ${id}: failed (${e.getMessage})"); replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- WorkflowGraf
      case GetWorkflowGrafs(from, size, replyTo) =>
        store.listGrafs(from, size).map(p => WorkflowGrafs(p.grafs, p.total)).onComplete(replyTo ! _)
        Behaviors.same

      case GetWorkflowGraf(id, replyTo) =>
        store.getGraf(id).onComplete(replyTo ! _)
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
        }.onComplete(replyTo ! _)
        Behaviors.same

      case DeleteWorkflowGraf(id, replyTo) =>
        store.delGraf(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- DetectorSchema
      case GetDetectorSchemas(from, size, replyTo) =>
        store.listDetectorSchemas(from, size).map(p => DetectorSchemas(p.schemas, p.total)).onComplete(replyTo ! _)
        Behaviors.same

      case GetDetectorSchema(id, replyTo) =>
        store.getDetectorSchema(id).map {
          case Some(d) => d
          case None    => throw new ErrNotFound(s"DetectorSchema: ${id}")
        }.onComplete(replyTo ! _)
        Behaviors.same

      case CreateDetectorSchema(req, replyTo) =>
        log.info(s"CreateDetectorSchema: ${req.name}")
        store.nextDetectorSchemaId.flatMap(id => store.addDetectorSchema(detectorSchemaFromReq(id, req))).onComplete(replyTo ! _)
        Behaviors.same

      case UpdateDetectorSchema(id, req, replyTo) =>
        log.info(s"UpdateDetectorSchema: ${id}")
        store.getDetectorSchema(id).map {
          case Some(d) => applyUpdate(d, req)
          case None    => throw new ErrNotFound(s"DetectorSchema: ${id}")
        }.flatMap(store.addDetectorSchema).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteDetectorSchema(id, replyTo) =>
        store.delDetectorSchema(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- DetectorConfig
      case GetDetectorConfigs(from, size, replyTo) =>
        store.listDetectorConfigs(from, size).map(p => DetectorConfigs(p.configs, p.total)).onComplete(replyTo ! _)
        Behaviors.same

      case GetDetectorConfig(id, replyTo) =>
        store.getDetectorConfig(id).map {
          case Some(d) => d
          case None    => throw new ErrNotFound(s"DetectorConfig: ${id}")
        }.onComplete(replyTo ! _)
        Behaviors.same

      case CreateDetectorConfig(req, replyTo) =>
        log.info(s"CreateDetectorConfig: ${req.name}")
        val r = for {
          schemaRef <- req.sid.map(sid => store.getDetectorSchema(sid)).getOrElse(Future.successful(None))
          id        <- store.nextDetectorConfigId
          saved     <- store.addDetectorConfig(detectorConfigFromReq(id, req, schemaRef))
        } yield saved
        r.onComplete(replyTo ! _)
        Behaviors.same

      case UpdateDetectorConfig(id, req, replyTo) =>
        log.info(s"UpdateDetectorConfig: ${id}")
        store.getDetectorConfig(id).map {
          case Some(d) => applyUpdate(d, req)
          case None    => throw new ErrNotFound(s"DetectorConfig: ${id}")
        }.flatMap(store.addDetectorConfig).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteDetectorConfig(id, replyTo) =>
        store.delDetectorConfig(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same
    }
}
