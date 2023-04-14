package io.syspulse.skel.lake.job.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.Store

import io.syspulse.skel.lake.job._

import io.syspulse.skel.lake.job.Job.ID

trait JobStore extends Store[Job,ID] {
  def getKey(j: Job): ID = j.id
  
  // update
  def update(job:Job):Try[Job]

  def +(job:Job):Try[JobStore]

  def submit(name:String,script:String,conf:Map[String,String],inputs:Map[String,String],uid:Option[UUID]):Try[Job]
  
  def del(id:ID):Try[JobStore]
  def ?(id:ID):Try[Job]
  def all:Seq[Job]
  def size:Long

  def getEngine:JobEngine

  // can be a queue
  @volatile
  var runningJobs:Seq[Job] = Seq()
  @volatile
  var running = false

  def enqueue(job:Job) = {
    runningJobs = runningJobs :+ job
  }

  def startFSM(implicit config:Config) = {
    val jobWatcherThr = new Thread() {
      val log = Logger(s"${this}")    
      override def run() = {
        running = true

        while(running) {
          log.info(s"watcher: ${runningJobs.size}")
          runningJobs = runningJobs.filter( j0 => {          
            log.info(s"watcher: ${j0}")
            var removing = false

            val j1:Try[Job] = j0.state match {
              case "unknown" =>
                Success(j0)

              case "starting" => 
                getEngine.get(j0)            

              case "available" => 
                if(j0.tsEnd.isDefined ) {
                  // script already finished
                  Success(j0.copy(state = "finished"))
                } else {
                  // run the job script
                  getEngine.run(j0,j0.src)
                }

              case "waiting" => 
                // script is running
                getEngine.ask(j0)

              case "finished" =>
                // removed from the list
                removing = true
                Success(j0)
            
            }

            if(j1.isSuccess && (j1.get.state != j0.state)) {
              update(j1.get)
            }
            if(j1.isFailure) {
              // update failed
              log.warn(s"job failed: ${j1}: removing")
              update(j0.copy(state = "finished"))
              removing = true
            }

            !removing || j1.isSuccess
          })

          Thread.sleep(config.poll)
        }
      }
    }
    
    jobWatcherThr.start()
  }
}

