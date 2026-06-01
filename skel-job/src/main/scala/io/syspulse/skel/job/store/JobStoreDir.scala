package io.syspulse.skel.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.job.Config
import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.job.Job
import io.syspulse.skel.job.server.JobJson._
import io.syspulse.skel.job.JobEngine
import io.syspulse.skel.job.server.Jobs

// Preload from file during start
class JobStoreDir(engine:JobEngine,dir:String = "store/")(implicit config:Config) extends StoreDir[Job,UUID](dir) with JobStore {
  val store = new JobStoreMem(engine)(config)

  def toKey(id:String):UUID = UUID(id)
  def all:Future[Seq[Job]] = store.all
  def size:Future[Long] = store.size

  // this is called on load, so we can update the status
  override def +(u:Job):Future[Job] = {
    super.+(u).flatMap(_ => store.+(u))
  }

  override def update(job:Job):Future[Job] = {
    writeFile(job)
    store.update(job)
  }

  // del does not delete the file, but only the status
  override def del(uid:UUID):Future[UUID] = {
    super.del(uid).flatMap(_ => store.del(uid))
  }

  override def ?(uid:UUID):Future[Job] = store.?(uid)

  override def ??(uid:Option[UUID],state:Option[String]=None):Future[Jobs] = store.??(uid,state)

  // load and fix statuses
  load(dir)

  // start FSM
  startFSM(config)

  override def loaded() = {
    import scala.concurrent.Await
    import scala.concurrent.duration.Duration
    val allJobs = Await.result(all, Duration(30, "seconds"))
    allJobs.foreach{ job => job.state match {
      case "unknown" =>
        // just started
        enqueue(job)

      case "starting" =>
        enqueue(job)

      case "available" =>
        enqueue(job)

      case "idle" =>
        enqueue(job)

      case "waiting" =>
        // script is running
        enqueue(job)

      case "finished" =>
        // finished

      case "deleted" =>
        enqueue(job)

      case _ =>
        enqueue(job)
    }}
  }

  def getEngine = store.getEngine
}
