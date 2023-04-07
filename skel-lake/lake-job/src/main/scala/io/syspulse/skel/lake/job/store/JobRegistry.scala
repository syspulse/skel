package io.syspulse.skel.lake.job.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.lake.job._
import scala.util.Try

import io.syspulse.skel.lake.job.server.{JobCreateReq, JobRes, Jobs}

object JobRegistry {
  val log = Logger(s"${this}")
  
  final case class GetJob(uid:Option[UUID],id: Job.ID, replyTo: ActorRef[Try[Job]]) extends Command
  final case class GetJobs(replyTo: ActorRef[Jobs]) extends Command
  final case class CreateJob(uid:Option[UUID], req: JobCreateReq, replyTo: ActorRef[Try[Job]]) extends Command  
  final case class DeleteJob(uid:Option[UUID], id: Job.ID, replyTo: ActorRef[JobRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: JobStore = null

  def apply(store: JobStore): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: JobStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetJobs(replyTo) =>
        val jj = store.all
        replyTo ! Jobs(jj,Some(jj.size))
        Behaviors.same

      case GetJob(uid, id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case DeleteJob(uid, id, replyTo) =>
        replyTo ! store.del(id).map(_ => JobRes("deleted",Some(id))).get
        Behaviors.same

      case CreateJob(uid:Option[UUID], req, replyTo) =>
        log.info(s"${req}")        
        val job = store.+(req.name,req.src,req.conf,req.inputs).map(_.copy(id = UUID.random,uid = uid))
        replyTo ! job
        Behaviors.same
    }
  }
}
