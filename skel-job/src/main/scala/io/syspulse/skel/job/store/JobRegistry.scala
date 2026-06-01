package io.syspulse.skel.job.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.job._
import scala.util.{Try,Success,Failure}
import scala.concurrent.ExecutionContext

import io.syspulse.skel.job.server.{JobSubmitReq, JobRes, Jobs}

object JobRegistry {
  val log = Logger(s"${this}")

  final case class GetJob(uid:Option[UUID],id: Job.ID, replyTo: ActorRef[Try[Job]]) extends Command
  final case class GetJobs(replyTo: ActorRef[Jobs]) extends Command
  final case class SubmitJob(uid:Option[UUID], req: JobSubmitReq, replyTo: ActorRef[Try[Job]]) extends Command
  final case class DeleteJob(uid:Option[UUID], id: Job.ID, replyTo: ActorRef[JobRes]) extends Command
  final case class FindJobs(uid:Option[UUID], state:Option[String], replyTo: ActorRef[Try[Jobs]]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: JobStore = null

  def apply(store: JobStore)(implicit config:Config): Behavior[Command] = {
    this.store = store
    registry(store)(config)
  }

  private def registry(store: JobStore)(config:Config): Behavior[Command] = {
    this.store = store
    implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

    Behaviors.receiveMessage {
      case GetJobs(replyTo) =>
        store.all.foreach(jj => replyTo ! Jobs(jj, Some(jj.size)))
        Behaviors.same

      case GetJob(uid, id, replyTo) =>
        store.?(id).onComplete(replyTo ! _)
        Behaviors.same

      case FindJobs(uid, state, replyTo) =>
        store.??(uid,state).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteJob(uid, id, replyTo) =>
        store.del(id).onComplete {
          case Success(_) => replyTo ! JobRes("deleted", Some(id))
          case Failure(e) => replyTo ! JobRes(s"error: ${e.getMessage}", Some(id))
        }
        Behaviors.same

      case SubmitJob(uid:Option[UUID], req, replyTo) =>
        log.info(s"${req}")
        store.submit(req.name,req.src,req.conf.getOrElse(Map()),req.inputs.getOrElse(Map()),uid,config.poll)
          .onComplete(replyTo ! _)
        Behaviors.same
    }
  }
}
