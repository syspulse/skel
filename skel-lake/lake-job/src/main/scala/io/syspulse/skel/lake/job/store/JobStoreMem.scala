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

  def all:Seq[Job] = jobs.values.flatMap{ j => 
    // request all jobs
    this.?(j.id).toOption
  }.toSeq

  def size:Long = jobs.size

  def +(name:String,script:String,conf:Seq[String],inputs:Seq[String],uid:Option[UUID]):Try[Job] = {
    log.info(s"add: ${name},${script.take(25)},${conf},${inputs}")

    // for {
    //   j1 <- engine.create(name,JobEngine.dataToConf(conf))
      
    //   j2 <- {
    //     var j:Try[Job] = engine.get(j1)
    //     while(j.isSuccess && j.get.state == "starting") {
    //       log.info(s"add: ${name}: sleeping poll ${config.poll}")
    //       Thread.sleep(config.poll)          
    //       j = engine.get(j1)
    //     } 
    //     j
    //   }
  
    //   j3 <- engine.run(j2,script,JobEngine.dataToVars(inputs))

    //   j4 <- this.+(j3)
    
    // } yield j3
    
    for {
      j1 <- engine.submit(name,script,conf,inputs,config.poll).map(_.copy(uid = uid))
      _ <- this.+(j1)
    } yield j1
    
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
          engine.ask(j) match {
            case Success(j2) => 
              // update store (persistance)
              this.+(j2).map(_ => j2)              
            case Failure(e) => 
              // not found, need to set to error
              val j2 = j.copy(result = Some("error"), output = Some(s"Failed to find: ${e}"))
              this.+(j2).map(_ => j2)              
          }
      }
    case None => 
      Failure(new Exception(s"not found: ${id}"))
  }

  def getEngine = engine
}
