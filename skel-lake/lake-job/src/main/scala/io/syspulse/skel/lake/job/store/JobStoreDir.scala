package io.syspulse.skel.lake.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.lake.job.Config
import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.lake.job.Job
import io.syspulse.skel.lake.job.server.JobJson._
import io.syspulse.skel.lake.job.JobEngine

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
  override def +(u:Job):Try[JobStoreDir] = {
    super.+(u).flatMap(_ => store.+(u)).map(_ => this)
  }

  override def update(job:Job):Try[Job] = {
    writeFile(job)
    store.update(job)
  }

  // del does not delete the file, but only the status
  override def del(uid:UUID):Try[JobStoreDir] = {
    super.del(uid).flatMap(_ => store.del(uid)).map(_ => this)
  }

  override def ?(uid:UUID):Try[Job] = store.?(uid)

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