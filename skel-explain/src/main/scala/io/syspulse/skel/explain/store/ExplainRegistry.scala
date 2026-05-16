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

  final case class GetRule(oid: String, rid: String, replyTo: ActorRef[Try[ExplainRule]]) extends Command
  final case class GetRules(oid: Option[String], replyTo: ActorRef[Try[ExplainRules]]) extends Command
  final case class CreateRule(oid: String, rid: String, req: ExplainRuleCreateReq, replyTo: ActorRef[Try[ExplainRuleRes]]) extends Command
  final case class UpdateRule(oid: String, rid: String, req: ExplainRuleUpdateReq, replyTo: ActorRef[Try[ExplainRuleRes]]) extends Command
  final case class DeleteRule(oid: String, rid: String, replyTo: ActorRef[Try[ExplainRuleRes]]) extends Command
  final case class Explain(rid: String, req: ExplainReq, replyTo: ActorRef[Try[ExplainRes]]) extends Command

  def apply(store: ExplainStore)(implicit config: Config): Behavior[io.syspulse.skel.Command] =
    registry(store)(config)

  private def registry(store: ExplainStore)(config: Config): Behavior[io.syspulse.skel.Command] = {
    implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(config.threads))

    Behaviors.receiveMessage {

      case GetRule(oid, rid, replyTo) =>
        replyTo ! store.get(oid, rid)
        Behaviors.same

      case GetRules(oid, replyTo) =>
        val rules = oid match {
          case Some(o) => store.findByOid(o)
          case None    => store.all
        }
        replyTo ! Success(ExplainRules(rules, Some(rules.size)))
        Behaviors.same

      case CreateRule(oid, rid, req, replyTo) =>
        log.info(s"CreateRule($oid,$rid): scripts='${req.scripts}', name=${req.name}")
        val rule = ExplainRule(oid = oid, rid = rid, scripts = req.scripts, name = req.name)
        store.+(rule) match {
          case Success(_) =>
            replyTo ! Success(ExplainRuleRes(oid, rid))
          case Failure(e) =>
            log.error(s"failed to create rule: $oid/$rid", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case UpdateRule(oid, rid, req, replyTo) =>
        log.info(s"UpdateRule($oid,$rid): scripts=${req.scripts}, name=${req.name}")
        store.get(oid, rid) match {
          case Success(existing) =>
            val updated = existing.copy(
              scripts = req.scripts.getOrElse(existing.scripts),
              name = req.name.orElse(existing.name),
              ts = System.currentTimeMillis()
            )
            store.+(updated) match {
              case Success(_)  => replyTo ! Success(ExplainRuleRes(oid, rid))
              case Failure(e)  =>
                log.error(s"failed to update rule: $oid/$rid", e)
                replyTo ! Failure(e)
            }
          case Failure(e) =>
            replyTo ! Failure(e)
        }
        Behaviors.same

      case DeleteRule(oid, rid, replyTo) =>
        store.del(oid, rid) match {
          case Success(_) =>
            replyTo ! Success(ExplainRuleRes(oid, rid))
          case Failure(e) =>
            log.error(s"failed to delete rule: $oid/$rid", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case Explain(rid, req, replyTo) =>
        val oid = req.oid.getOrElse(ExplainRule.DEF_OID)

        // Step 1: find rule by oid/rid; Step 2: fallback to default oid; Step 3: error
        val ruleOpt: Option[ExplainRule] =
          store.get(oid, rid).toOption
            .orElse(if (oid != ExplainRule.DEF_OID) store.get(ExplainRule.DEF_OID, rid).toOption else None)

        ruleOpt match {
          case None =>
            replyTo ! Failure(new Exception(s"ScriptFlow not found: oid='$oid', rid='$rid'"))

          case Some(rule) =>
            val engines = rule.scripts.flatMap(uri => ScriptFlow.parseUri(uri.trim).toOption)
            val scriptFlow = ScriptFlow.build(engines)
            val scriptNames = rule.scripts.map(uri => uri.split("://")(0)).filter(_.nonEmpty)

            val input = req.data.compactPrint
            val dataMap: Map[String, Any] = Map(
              "oid" -> rule.oid,
              "rid" -> rid,
              "schema" -> req.schema.map(_.compactPrint).getOrElse("")
            )

            scriptFlow.run("", input, dataMap) match {
              case Success(explanation) =>
                replyTo ! Success(ExplainRes(
                  explanation = explanation,
                  ts = System.currentTimeMillis(),
                  scripts = scriptNames,
                  oid = Some(rule.oid)  // return the actual rule's oid (not requested oid)
                ))
              case Failure(e) =>
                log.error(s"ScriptFlow failed: oid='$oid', rid='$rid'", e)
                replyTo ! Failure(e)
            }
        }
        Behaviors.same
    }
  }
}
