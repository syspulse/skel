package io.syspulse.skel.lake.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.lake.job.Config
import io.syspulse.skel.lake.job.Job
import io.syspulse.skel.lake.job.JobEngine

class JobStoreMem(engine:JobEngine)(implicit config:Config) extends JobStore {
  val log = Logger(s"${this}")
  
  var jobs: Map[UUID,Job] = Map()
  
  def all:Seq[Job] = jobs.values.toSeq
  // def all:Seq[Job] = jobs.values.flatMap{ j => 
  //   // request all jobs
  //   this.?(j.id).toOption
  // }.toSeq

  def size:Long = jobs.size



  override def +(job:Job):Try[JobStore] = { 
    log.info(s"add: ${job}")
    jobs = jobs + (job.id -> job)
    Success(this)
  }

  def update(job:Job):Try[Job] = {
    log.info(s"update: ${job}")
    // this should overwrite 
    jobs = jobs + (job.id -> job)
    Success(job)
  }


  // jobs are not removed, but status is changed
  def del(id:UUID):Try[JobStore] = { 
    log.info(s"del: ${id}")
    this.?(id) match {
      case Success(job) => 
        engine.del(job).map(_ => this)
      case Failure(e) => Failure(e)
    }    
  }

  def ?(id:UUID):Try[Job] = jobs.get(id) match {
    case Some(j) => Success(j)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  // def ?(id:UUID):Try[Job] = jobs.get(id) match {
  //   case Some(j) => 
  //     // ask engine only if j is not completed
  //     j.result match {
  //       case Some("error") | Some("ok") =>
  //         Success(j)
  //       case _ =>
  //         engine.ask(j) match {
  //           case Success(j2) => 
  //             // update store (persistance)
  //             this.+(j2).map(_ => j2)              
  //           case Failure(e) => 
  //             // not found, need to set to error
  //             val j2 = j.copy(result = Some("error"), output = Some(s"Failed to find: ${e}"))
  //             this.+(j2).map(_ => j2)              
  //         }
  //     }
  //   case None => 
  //     Failure(new Exception(s"not found: ${id}"))
  // }

  def getEngine = engine
}
