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
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.dsl.AssemblyDSL

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
  final case class CreateConfig(req: WorkflowConfigCreateReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class CreateConfigDsl(req: WorkflowConfigDslReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class UpdateConfig(id: Int, req: WorkflowConfigUpdateReq, replyTo: ActorRef[Try[WorkflowConfig]]) extends Command
  final case class DeleteConfig(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

  // ---- WorkflowGraf ----
  final case class GetGrafs(from: Option[Long], size: Option[Long], replyTo: ActorRef[Try[WorkflowGrafs]]) extends Command
  final case class GetGraf(id: Int, replyTo: ActorRef[Try[WorkflowGraf]]) extends Command
  final case class CreateGraf(req: WorkflowGrafCreateReq, replyTo: ActorRef[Try[WorkflowGraf]]) extends Command
  final case class DeleteGraf(id: Int, replyTo: ActorRef[WorkflowActionRes]) extends Command

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
        AssemblyDSL.assemble(req.pipeline, store, req.wid, req.name)
          .map(_.config.getOrElse(throw new Exception("assembly did not produce a WorkflowConfig")))
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
    }
}
