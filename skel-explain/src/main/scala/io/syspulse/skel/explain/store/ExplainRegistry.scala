package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

import spray.json._

import io.syspulse.skel.Command
import io.syspulse.skel.explain._
import io.syspulse.skel.explain.server._
import io.syspulse.skel.script.ScriptFlow

object ExplainRegistry {
  val log = Logger(s"${this}")

  final case class GetRule(oid: Option[String], rid: String, replyTo: ActorRef[Try[Explain]]) extends Command
  final case class GetRules(oid: Option[String], rid: Option[String] = None, from: Option[Long] = None, size: Option[Long] = None, replyTo: ActorRef[Try[Explains]]) extends Command
  final case class SearchRules(search: String, from: Option[Long] = None, size: Option[Long] = None, replyTo: ActorRef[Try[Explains]]) extends Command
  final case class CreateRule(oid: String, rid: String, req: ExplainCreateReq, replyTo: ActorRef[Try[ExplaineActionRes]]) extends Command
  final case class UpdateRule(oid: String, rid: String, req: ExplainUpdateReq, replyTo: ActorRef[Try[ExplaineActionRes]]) extends Command
  final case class DeleteRule(oid: String, rid: String, replyTo: ActorRef[Try[ExplaineActionRes]]) extends Command
  final case class DeleteRules(oid: String, replyTo: ActorRef[Try[Explains]]) extends Command
  final case class RunExplain(req: ExplainReq, style: String, replyTo: ActorRef[Try[ExplainRes]]) extends Command

  def apply(store: ExplainStore)(implicit config: Config): Behavior[io.syspulse.skel.Command] =
    registry(store)(config)

  private def registry(store: ExplainStore)(config: Config): Behavior[io.syspulse.skel.Command] = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threads))

    Behaviors.receiveMessage {

      case GetRule(oid, rid, replyTo) =>
        store.get(oid, rid).onComplete {
          case Success(r) => replyTo ! Success(r)
          case Failure(e) => replyTo ! Failure(e)
        }
        Behaviors.same

      case GetRules(oid, rid, from, size, replyTo) =>
        store.list(oid, rid, from, size).onComplete {
          case Success(p) => replyTo ! Success(Explains(p.rules, Some(p.total)))
          case Failure(e) => replyTo ! Failure(e)
        }
        Behaviors.same

      case SearchRules(search, from, size, replyTo) =>
        val fut = (from, size) match {
          case (Some(_), None) | (None, Some(_)) =>
            Future.failed(new IllegalArgumentException("from and size must both be set for paging"))
          case _ => store.search(search, from, size)
        }
        fut.onComplete {
          case Success(p) => replyTo ! Success(Explains(p.rules, Some(p.total)))
          case Failure(e) => replyTo ! Failure(e)
        }
        Behaviors.same

      case CreateRule(oid, rid, req, replyTo) =>
        log.info(s"CreateRule($oid,$rid): scripts='${req.scripts}', name=${req.name}, desc=${req.desc}, sid=${req.sid}")
        val rule = io.syspulse.skel.explain.Explain(oid = Option(oid).filter(_.nonEmpty), rid = rid, scripts = req.scripts, name = req.name, desc = req.desc, sid = req.sid, meta = req.meta)
        store.+(rule).onComplete {
          case Success(_) =>
            replyTo ! Success(ExplaineActionRes(Option(oid).filter(_.nonEmpty), rid))
          case Failure(e) =>
            log.error(s"failed to create rule: $oid/$rid", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case UpdateRule(oid, rid, req, replyTo) =>
        log.info(s"UpdateRule($oid,$rid): scripts=${req.scripts}, name=${req.name}, desc=${req.desc}, sid=${req.sid}")
        store.get(Option(oid).filter(_.nonEmpty), rid).flatMap { existing =>
          val updated = existing.copy(
            scripts = req.scripts.getOrElse(existing.scripts),
            name = req.name.orElse(existing.name),
            desc = req.desc.orElse(existing.desc),
            sid = req.sid.orElse(existing.sid),
            meta = req.meta.orElse(existing.meta),
            ts = System.currentTimeMillis()
          )
          store.+(updated)
        }.onComplete {
          case Success(_) => replyTo ! Success(ExplaineActionRes(Option(oid).filter(_.nonEmpty), rid))
          case Failure(e) =>
            log.error(s"failed to update rule: $oid/$rid", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case DeleteRule(oid, rid, replyTo) =>
        log.info(s"DeleteRule($oid,$rid)")
        store.del(Option(oid).filter(_.nonEmpty), rid).onComplete {
          case Success(_) =>
            replyTo ! Success(ExplaineActionRes(Option(oid).filter(_.nonEmpty), rid))
          case Failure(e) =>
            log.error(s"failed to delete rule: $oid/$rid", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case DeleteRules(oid, replyTo) =>
        log.info(s"DeleteRules($oid)")
        store.delByOid(Option(oid).filter(_.nonEmpty)).onComplete {
          case Success(deleted) =>
            replyTo ! Success(Explains(deleted, Some(deleted.size)))
          case Failure(e) =>
            log.error(s"failed to delete rules for oid: $oid", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case RunExplain(req, style, replyTo) =>
        log.info(s"RunExplain(${req.oid},${req.rid},$style)")
        val oid = req.oid
        val rid = req.rid.getOrElse("")

        val ruleOptFut: Future[Option[io.syspulse.skel.explain.Explain]] =
          store.get(oid, rid).map(Some(_)).recoverWith { case _ =>
            if (oid.nonEmpty) store.get(Explain.DEF_OID, rid).map(Some(_)).recover { case _ => None }
            else Future.successful(None)
          }

        ruleOptFut.foreach {
          case None =>
            replyTo ! Failure(new Exception(s"ScriptFlow not found: oid='${oid}', rid='$rid'"))

          case Some(rule) =>
            val engines = rule.scripts.flatMap(s => ScriptFlow.resolve(s.typ, s.src, s.opts).toOption)
            val scriptFlow = ScriptFlow.build(engines)
            val scriptNames = rule.scripts.map(_.typ)

            val input = ExplainJson.buildInput(req)

            val dataMap: Map[String, Any] = Map(
              "oid"   -> oid.getOrElse(""),
              "rid"   -> rid,
              "sid"   -> rule.sid.getOrElse(""),
              "style" -> style
            )

            scriptFlow.run("", input, dataMap) match {
              case Success(explanation) =>
                replyTo ! Success(ExplainRes(
                  explanation = explanation,
                  ts = System.currentTimeMillis(),
                  scripts = scriptNames,
                  style = Option(style).filter(_.nonEmpty),
                  rid = rule.rid,
                  oid = rule.oid,
                  meta = rule.meta
                ))
              case Failure(e) =>
                log.error(s"ScriptFlow failed: oid='${oid}', rid='$rid'", e)
                replyTo ! Failure(e)
            }
        }
        Behaviors.same
    }
  }
}
