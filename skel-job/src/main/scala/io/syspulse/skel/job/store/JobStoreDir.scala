package io.syspulse.skel.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

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
  def all:Seq[Job] = store.all
  def size:Long = store.size
  
  // this is job submission
  // def submit(name:String,script:String,conf:Map[String,String],inputs:Map[String,String],uid:Option[UUID]):Try[Job] = {
  //   store.submit(name,script,conf,inputs,uid).flatMap(j => super.+(j).map(_ => j))
  // }

  // this is called on load, so we can update the status
  override def +(u:Job):Try[Job] = {
    super.+(u).flatMap(_ => store.+(u))
  }

  override def update(job:Job):Try[Job] = {
    writeFile(job)
    store.update(job)
  }

  // del does not delete the file, but only the status
  override def del(uid:UUID):Try[UUID] = {
    super.del(uid).flatMap(_ => store.del(uid))
  }

  override def ?(uid:UUID):Try[Job] = store.?(uid)

  override def ??(uid:Option[UUID],state:Option[String]=None):Try[Jobs] = store.??(uid,state)

  // load and fix statuses
  load(dir)

  // start FSM
  startFSM(config)

  override def loaded() = {
    all.foreach{ job => job.state match {      
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