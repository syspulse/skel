package io.syspulse.skel.job.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.Store
import io.syspulse.skel.notify.NotifyService
import io.syspulse.skel.notify.NotifySeverity

import io.syspulse.skel.job._
import io.syspulse.skel.job.Job.ID
import io.syspulse.skel.job.server.Jobs

trait JobStore extends Store[Job,ID] {
  private val log = Logger(s"${this}")

  def getKey(j: Job): ID = j.id
  
  // update
  def update(job:Job):Try[Job]

  def +(job:Job):Try[JobStore]

  //def submit(name:String,script:String,conf:Map[String,String],inputs:Map[String,String],uid:Option[UUID]):Try[Job]
  
  def del(id:ID):Try[JobStore]
  def ?(id:ID):Try[Job]

  def ??(uid:Option[UUID],state:Option[String]=None):Try[Jobs]
  def all:Seq[Job]
  def size:Long

  def getEngine:JobEngine

  // can be a queue
  @volatile
  var runningJobs:Seq[Job] = Seq()
  @volatile
  var running = false

  def enqueue(job:Job) = {
    log.info(s"${job} ->> queue(${runningJobs.size})")
    runningJobs = runningJobs :+ job
  }

  def startFSM(implicit config:Config) = {
    val jobWatcherThr = new Thread() {
      
      override def run() = {
        running = true

        while(running) {
          if(runningJobs.size > 0)
            log.info(s"watcher: ${runningJobs.size}")
            
          runningJobs = runningJobs.flatMap( j0 => {          
            log.info(s"watcher: ${j0}")
            var removing = false

            val j1:Try[Job] = j0.state match {
              case "unknown" =>
                Success(j0)

              case "starting" | "not_started" => 
                getEngine.get(j0)            
              
              case "waiting" | "running" | "busy" => 
                // script is running
                getEngine.ask(j0)

              case "idle" | "available" => 
                if(j0.tsEnd.isDefined ) {
                  // script already finished, stop it
                  getEngine.del(j0)
                  //Success(j0.copy(state = "finished"))
                } else {
                  // run the job script
                  val src = JobEngine.toSrc(j0.src,j0.inputs)
                  log.debug(s"src=${src}")
                  getEngine.run(j0,src)
                }

              case "deleted" => 
                // when we finish by deleting it
                Success(j0.copy(state = "finished"))

              case "finished" | "error" | "dead" | "killed" | "success" =>
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
              update(j0.copy(state = "finished", result = Some(s"${j1.toString}")))
              removing = true
            }

            if(removing || j1.isFailure) {
              // send notify
              NotifyService.notify(
                s"user://${j0.uid.getOrElse("")}",
                subj = j0.name,
                msg = s"""{"typ":"job","id":"${j0.id}"}""",
                if(j1.isFailure) Some(NotifySeverity.ERROR) else Some(NotifySeverity.INFO),
                Some("sys.job"))
              None
            }
            else
              Some(j1.get)
          })

          Thread.sleep(config.poll)
        }
      }
    }
    
    jobWatcherThr.start()
  }

  def submit(name:String,script:String,conf:Map[String,String],inputs:Map[String,Any],uid:Option[UUID],poll:Long):Try[Job] = {
    log.info(s"submit: ${name},${script.take(25)},${conf},${inputs}")

    // val src = if(script.startsWith("file://"))
    //     os.read(os.Path(script.stripPrefix("file://"),os.pwd))
    //   else
    //     script.mkString(" ")

    //val src = JobEngine.toSrc(script,inputs)
    //log.info(s"src=${src}")
        
  
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
      j1 <- getEngine.submit(name,script,conf,inputs,poll).map(_.copy(uid = uid,inputs = inputs))
      _ <- this.+(j1)
      _ <- {
        enqueue(j1)
        Success(j1)
      }
    } yield j1
    
  }
}

