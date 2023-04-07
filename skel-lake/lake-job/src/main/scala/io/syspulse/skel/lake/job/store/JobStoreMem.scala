package io.syspulse.skel.lake.job.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.lake.job.Job
import io.syspulse.skel.lake.job.JobEngine

class JobStoreMem(engine:JobEngine) extends JobStore {
  val log = Logger(s"${this}")
  
  var jobs: Map[UUID,Job] = Map()

  def all:Seq[Job] = jobs.values.toSeq

  def size:Long = jobs.size

  def +(name:String,script:String,conf:Seq[String],inputs:Seq[String]):Try[Job] = {
    log.info(s"create: ${name},${script.take(25)},${conf},${inputs}")
    val j = engine.create(name,JobEngine.dataToConf(conf))
    j match {
      case Success(job) => 
        for {
          j1 <- engine.run(job,script,JobEngine.dataToVars(inputs))
          j2 <- this.+(j1)
        } yield j1

      case f => f
    }
  }

  override def +(job:Job):Try[JobStore] = { 
    log.info(s"add: ${job}")
    jobs = jobs + (job.id -> job)        
    Success(this)
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
    case Some(j) => 
      // ask engine only if j is not completed
      j.result match {
        case Some("error") | Some("ok") =>
          Success(j)
        case _ =>
          engine.ask(j).map(j2 => {
            this.+(j2)
            j2
          })
      }
    case None => 
      Failure(new Exception(s"not found: ${id}"))
  }

  def getEngine = engine
}
