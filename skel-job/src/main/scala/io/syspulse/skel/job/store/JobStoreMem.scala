package io.syspulse.skel.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.job.Config
import io.syspulse.skel.job.Job
import io.syspulse.skel.job.JobEngine
import io.syspulse.skel.job.server.Jobs

class JobStoreMem(engine:JobEngine)(implicit config:Config) extends JobStore {
  val log = Logger(s"${this}")

  var jobs: Map[UUID,Job] = Map()

  def all:Future[Seq[Job]] = Future.successful(jobs.values.toSeq)

  def size:Future[Long] = Future.successful(jobs.size.toLong)

  override def +(job:Job):Future[Job] = {
    log.info(s"add: ${job}")
    jobs = jobs + (job.id -> job)
    Future.successful(job)
  }

  def update(job:Job):Future[Job] = {
    log.info(s"update: ${job}")
    // this should overwrite
    jobs = jobs + (job.id -> job)
    Future.successful(job)
  }


  // jobs are not removed, but status is changed
  def del(id:UUID):Future[UUID] = {
    log.info(s"del: ${id}")
    jobs.get(id) match {
      case Some(job) =>
        engine.del(job) match {
          case Success(_) => Future.successful(id)
          case Failure(e) => Future.failed(e)
        }
      case None => Future.failed(new Exception(s"not found: ${id}"))
    }
  }

  def ?(id:UUID):Future[Job] = jobs.get(id) match {
    case Some(j) => Future.successful(j)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def ??(uid:Option[UUID],state:Option[String]=None):Future[Jobs] = {
    log.info(s"??: ${uid},${state}")
    val jj = jobs.values.filter( j => {
      //log.debug(s"??: ${uid}: ${j.uid}: state=${state}")
      (uid == None || j.uid == uid) &&
      (state == None ||
        (
          if(state.get.startsWith("!"))
            state.get.toLowerCase.stripPrefix("!") != j.state.toLowerCase
          else
            state.get.toLowerCase == j.state.toLowerCase
        )
      )
    }).toSeq
    Future.successful(Jobs(jj,Some(jj.size)))
  }

  def getEngine = engine
}
