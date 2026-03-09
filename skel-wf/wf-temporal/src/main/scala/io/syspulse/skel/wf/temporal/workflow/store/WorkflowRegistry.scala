package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.util.Util
import io.syspulse.skel.Command

import io.hacken.ext.wf.WorkflowSchema
import io.syspulse.skel.wf.temporal.workflow.server._

object WorkflowRegistry {
  val log = Logger(s"${this}")

  final case class GetWorkflow(id:Int, replyTo: ActorRef[Try[WorkflowSchema]]) extends Command
  final case class GetWorkflows(replyTo: ActorRef[Try[Workflows]]) extends Command
  final case class CreateWorkflow(req:WorkflowCreateReq, replyTo: ActorRef[Try[WorkflowRes]]) extends Command
  final case class UpdateWorkflow(id:Int, req:WorkflowUpdateReq, replyTo: ActorRef[Try[WorkflowRes]]) extends Command
  final case class DeleteWorkflow(id:Int, replyTo: ActorRef[Try[WorkflowRes]]) extends Command

  def apply(store: WorkflowStore): Behavior[io.syspulse.skel.Command] = {
    registry(store)
  }

  private def registry(store: WorkflowStore): Behavior[io.syspulse.skel.Command] = {
    Behaviors.receiveMessage {

      case GetWorkflow(id, replyTo) =>
        val r = store.???(id)
        replyTo ! r
        Behaviors.same

      case GetWorkflows(replyTo) =>
        val r = store.all
        replyTo ! Success(Workflows(r, total = Some(r.size)))
        Behaviors.same

      case UpdateWorkflow(id, req, replyTo) =>
        log.info(s"UpdateWorkflow($id),${req.name},${req.title}")

        val r = store
          .???(id)
          .map(w => w.copy(
            updatedAt = System.currentTimeMillis(),
            name = if(req.name.isDefined) req.name.get else w.name,
            title = if(req.title.isDefined) req.title.get else w.title,
            description = if(req.description.isDefined) req.description.get else w.description,
            version = if(req.version.isDefined) req.version.get else w.version,
            tags = if(req.tags.isDefined) req.tags.get else w.tags,
            nodes = if(req.nodes.isDefined) req.nodes.get else w.nodes,
            connections = if(req.connections.isDefined) req.connections.get else w.connections
          ))
          .flatMap(w => store.+(w))

        r match {
          case Success(w) =>
            replyTo ! Success(WorkflowRes(w.id))
          case Failure(e) =>
            log.error(s"failed to update workflow: ${id}", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case CreateWorkflow(req, replyTo) =>
        log.info(s"CreateWorkflow,${req.name}")

        // Find max ID and increment
        val maxId = if (store.all.isEmpty) 0 else store.all.map(_.id).max
        val newId = maxId + 1

        val r = store.+(
          WorkflowSchema(
            id = newId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            status = "ACTIVE",
            name = req.name,
            version = req.version.getOrElse("1.0.0"),
            title = req.title.getOrElse(req.name),
            description = req.description.getOrElse(""),
            author = req.author.getOrElse(""),
            icon = req.icon,
            faq = req.faq,
            tags = req.tags.getOrElse(Seq.empty),
            nodes = req.nodes.getOrElse(Seq.empty),
            connections = req.connections.getOrElse(Seq.empty)
          )
        )

        r match {
          case Success(w) =>
            replyTo ! Success(WorkflowRes(w.id))
          case Failure(e) =>
            log.error(s"failed to create workflow", e)
            replyTo ! Failure(e)
        }
        Behaviors.same

      case DeleteWorkflow(id, replyTo) =>
        val r = store.del(id)

        r match {
          case Success(_) =>
            replyTo ! Success(WorkflowRes(id))
          case Failure(e) =>
            log.error(s"failed to delete workflow: ${id}", e)
            replyTo ! Failure(e)
        }
        Behaviors.same
    }
  }
}
