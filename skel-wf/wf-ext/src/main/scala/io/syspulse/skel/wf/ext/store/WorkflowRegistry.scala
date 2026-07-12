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
import io.syspulse.skel.wf.ext.engine.{TrackMapper, EngineWorkflow}

object WorkflowRegistry {
  val log = Logger(s"${this}")

  // ---- WorkflowSchema ----
  final case class GetSchemas(from: Option[Long], size: Option[Long], detail: Boolean, replyTo: ActorRef[Try[WorkflowSchemas]]) extends Command
  final case class GetSchema(id: Int, detail: Boolean, replyTo: ActorRef[Try[WorkflowSchemaView]]) extends Command
  final case class CreateSchema(req: WorkflowSchemaCreateReq, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class CreateSchemaDsl(req: WorkflowSchemaDslReq, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class UpdateSchema(id: Int, req: WorkflowSchemaUpdateReq, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class DeleteSchema(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

  // ---- WorkflowConfig ----
  final case class GetConfigs(from: Option[Long], size: Option[Long], detail: Boolean, replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command
  final case class GetConfig(id: Int, detail: Boolean, replyTo: ActorRef[Try[WorkflowConfigView]]) extends Command
  final case class GetConfigByXid(xid: String, replyTo: ActorRef[Option[WorkflowConfig]]) extends Command
  final case class GetConfigsByOid(oid: String, replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command
  // resolve WorkflowConfig(s) (+ all DetectorConfigs) by runtimeId (xid) or workflowId (meta.wid), many ids in one call.
  // typ forces the resolution mode: Some("rid") -> by xid, Some("wid") -> by workflowId, None -> auto-detect (UUID -> rid).
  final case class ResolveConfigs(ids: Seq[String], typ: Option[String], replyTo: ActorRef[Try[WorkflowConfigs]]) extends Command

  val RESOLVE_RID = "rid"  // resolve by runtimeId (WorkflowConfig.xid)
  val RESOLVE_WID = "wid"  // resolve by workflowId (WorkflowConfig.meta.wid / name)
  final case class CreateConfig(req: WorkflowConfigCreateReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class CreateConfigDsl(req: WorkflowConfigDslReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // assembly WorkflowConfig from DSL (bracket shorthand accepted) - same as the `assembly` command
  final case class AssemblyConfig(req: WorkflowConfigDslReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  // assembly + bind to a runtime resolved on the Engine (runtime == None -> fallback xid=fallbackId) - same as `assembly-link`
  final case class AssemblyLinked(req: WorkflowConfigDslReq, runtime: Option[EngineWorkflow], fallbackId: String, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class UpdateConfig(id: Int, req: WorkflowConfigUpdateReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class DeleteConfig(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

  // ---- WorkflowGraf ----
  final case class GetGrafs(from: Option[Long], size: Option[Long], replyTo: ActorRef[Try[WorkflowGrafs]]) extends Command
  final case class GetGraf(id: Int, replyTo: ActorRef[Try[WorkflowGraf]]) extends Command
  final case class CreateGraf(req: WorkflowGrafCreateReq, replyTo: ActorRef[Try[WorkflowGraf]]) extends Command
  final case class DeleteGraf(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

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

  def apply(store: WorkflowStore): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val ec: ExecutionContext = context.executionContext
      registry(store, context)
    }

  // ---------------------------------------------------------------- view assembly
  private def schemaView(store: WorkflowStore, s: WorkflowSchema, full: Boolean)(implicit ec: ExecutionContext): Future[WorkflowSchemaView] =
    if (!full) Future.successful(WorkflowSchemaView(s, None))
    else schemaDetectors(store, Seq(s)).map(m => WorkflowSchemaView(s, Some(m)))

  private def schemaDetectors(store: WorkflowStore, ss: Seq[WorkflowSchema])(implicit ec: ExecutionContext): Future[Map[String, DetectorSchema]] = {
    val sids = ss.flatMap(_.graph.nodes.values.map(_.sid)).toSet.toSeq
    Future.sequence(sids.map(id => store.getDetectorSchema(id).map(_.map(id.toString -> _))))
      .map(_.flatten.toMap)
  }

  private def configView(store: WorkflowStore, c: WorkflowConfig, full: Boolean)(implicit ec: ExecutionContext): Future[WorkflowConfigView] =
    if (!full) Future.successful(WorkflowConfigView(c, None))
    else configDetectors(store, Seq(c)).map(m => WorkflowConfigView(c, Some(m)))

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
   * Returns the matched configs (deduped) plus ALL their DetectorConfigs.
   */
  private def resolveConfigs(store: WorkflowStore, ids: Seq[String], typ: Option[String])(implicit ec: ExecutionContext): Future[WorkflowConfigs] =
    store.allConfigs.flatMap { all =>
      def byRid(id: String) = all.filter(_.xid.contains(id))
      def byWid(id: String) = all.filter(c => configWid(c).contains(id) || c.name == id)
      val mode = typ.map(_.trim.toLowerCase)
      val found = ids.flatMap { id =>
        mode match {
          case Some(RESOLVE_RID) => byRid(id)
          case Some(RESOLVE_WID) => byWid(id)
          case _                 => if (TrackMapper.isUuid(id)) byRid(id) else byWid(id)
        }
      }.distinctBy(_.id)
      configDetectors(store, found).map(dets => WorkflowConfigs(found, found.size.toLong, Some(dets)))
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
  private def registry(store: WorkflowStore, context: ActorContext[Command])(implicit ec: ExecutionContext): Behavior[Command] =
    Behaviors.receiveMessage {

      // -------------------------------------------------- WorkflowSchema
      case GetSchemas(from, size, detail, replyTo) =>
        store.listSchemas(from, size).flatMap { p =>
          if (!detail) Future.successful(WorkflowSchemas(p.schemas, p.total, None))
          else schemaDetectors(store, p.schemas).map(m => WorkflowSchemas(p.schemas, p.total, Some(m)))
        }.onComplete(replyTo ! _)
        Behaviors.same

      case GetSchema(id, detail, replyTo) =>
        store.getSchema(id).flatMap(s => schemaView(store, s, detail)).onComplete(replyTo ! _)
        Behaviors.same

      case CreateSchema(req, replyTo) =>
        log.info(s"CreateSchema: ${req}")

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

      case CreateSchemaDsl(req, replyTo) =>
        AssemblyDSL.buildSchema(req.pipeline, store, req.wid, req.name)
          .map(_.schema).onComplete(replyTo ! _)
        Behaviors.same

      case UpdateSchema(id, req, replyTo) =>
        log.info(s"UpdateSchema: ${req}")

        store.getSchema(id).map(s => applyUpdate(s, req)).flatMap(store.addSchema).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteSchema(id, replyTo) =>
        log.info(s"DeleteSchema: ${id}")

        store.delSchema(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- WorkflowConfig
      case GetConfigs(from, size, detail, replyTo) =>
        store.listConfigs(from, size).flatMap { p =>
          if (!detail) Future.successful(WorkflowConfigs(p.configs, p.total, None))
          else configDetectors(store, p.configs).map(m => WorkflowConfigs(p.configs, p.total, Some(m)))
        }.onComplete(replyTo ! _)
        Behaviors.same

      case GetConfig(id, detail, replyTo) =>
        store.getConfig(id).flatMap(c => configView(store, c, detail)).onComplete(replyTo ! _)
        Behaviors.same

      case GetConfigByXid(xid, replyTo) =>
        store.findConfigByXid(xid).onComplete {
          case Success(opt) => replyTo ! opt
          case Failure(_)   => replyTo ! None
        }
        Behaviors.same

      case GetConfigsByOid(oid, replyTo) =>
        store.findConfigByOid(oid).map(cs => WorkflowConfigs(cs, cs.size.toLong, None)).onComplete(replyTo ! _)
        Behaviors.same

      case ResolveConfigs(ids, typ, replyTo) =>
        log.info(s"ResolveConfigs: type=${typ.getOrElse("auto")} ids=${ids.mkString(",")}")
        resolveConfigs(store, ids, typ).onComplete(replyTo ! _)
        Behaviors.same

      case CreateConfig(req, replyTo) =>
        log.info(s"CreateConfig: ${req}")

        val r = for {
          schema <- store.getSchema(req.sid)
          id     <- store.nextConfigId
          c       = WorkflowConfig.from(id, schema, req.name, req.oid, req.pid, req.xid)
          saved  <- store.addConfig(c)
        } yield saved
        r.onComplete(replyTo ! _)
        Behaviors.same

      case CreateConfigDsl(req, replyTo) =>
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name).onComplete(replyTo ! _)
        Behaviors.same

      case AssemblyConfig(req, replyTo) =>
        log.info(s"AssemblyConfig: ${req.pipeline}")
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name).onComplete(replyTo ! _)
        Behaviors.same

      case AssemblyLinked(req, runtime, fallbackId, replyTo) =>
        log.info(s"AssemblyLinked: id=${fallbackId} runtime=${runtime.map(_.id)} ${req.pipeline}")
        WorkflowAssembly.assembly(req.pipeline, store, req.wid, req.name)
          .flatMap(cfg0 => WorkflowAssembly.link(cfg0, runtime, fallbackId, store))
          .onComplete(replyTo ! _)
        Behaviors.same

      case UpdateConfig(id, req, replyTo) =>
        log.info(s"UpdateConfig: ${req}")

        store.getConfig(id).map(c => applyUpdate(c, req)).flatMap(store.addConfig).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteConfig(id, replyTo) =>
        log.info(s"DeleteConfig: ${id}")

        store.delConfig(id).onComplete {
          case Success(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.OK, Some(id))
          case Failure(_) => replyTo ! WorkflowActionRes(WorkflowActionRes.NOT_FOUND, Some(id))
        }
        Behaviors.same

      // -------------------------------------------------- WorkflowGraf
      case GetGrafs(from, size, replyTo) =>
        store.listGrafs(from, size).map(p => WorkflowGrafs(p.grafs, p.total)).onComplete(replyTo ! _)
        Behaviors.same

      case GetGraf(id, replyTo) =>
        store.getGraf(id).onComplete(replyTo ! _)
        Behaviors.same

      case CreateGraf(req, replyTo) =>
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

      case DeleteGraf(id, replyTo) =>
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
