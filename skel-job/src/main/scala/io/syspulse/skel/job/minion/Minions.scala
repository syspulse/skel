package io.syspulse.skel.job.minion

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Future, Promise}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import scala.concurrent.ExecutionContext
import scala.collection.immutable.Queue

import io.syspulse.skel.cron.Cron
import io.syspulse.skel.util.Util

// =========================================================================================================
trait Minion[J,R] {
  def run():R
  def onError(e:Throwable):Unit
  def onSuccess(r:R):Unit
}

class JobMaster[J,R](freq:String = "1000", threads:Int = 2, timeout0:Long = 1 * 60 * 1000L,max:Long = 10L) {
  val log = Logger(this.getClass)

  private val executor = java.util.concurrent.Executors.newFixedThreadPool(threads)
  implicit val ex: ExecutionContext = ExecutionContext.fromExecutor(executor)
  
  case class JobRun(job:Minion[J,R], future:Future[R], ts0:Long = System.currentTimeMillis())

  @volatile
  var queue:Queue[JobRun] = Queue.empty
  
  val cron = Cron(
    (_) => {
      process()
      true
    },
    s"${freq}"
  )
  
  def +(job:Minion[J,R]):Future[R] = this.synchronized {
    if (queue.size >= max) {
      log.warn(s"Job queue full: ${queue.size} (max=${max})")
      return Future.failed(new Exception(s"Job queue full: ${queue.size}"))
    }
    
    // Execute the job asynchronously
    val future = Future {
      job.run()
    }(ex)
    // for testing only
    //val future = Future.failed(new java.util.concurrent.RejectedExecutionException("simulation"))

    // future could have already failed, may be check here ?    
    val jr = JobRun(job, future)
    queue = queue.+:(jr)
    
    log.info(s"add: ${jr} -> Jobs(${queue.size})")
    future
  }

  def start(): Unit = this.synchronized {
    cron.start()
    log.info(s"[start]: ${cron}: ${freq}ms")      
  }

  def stop(): Unit = this.synchronized {
    log.info(s"[stop]: ${cron}: Jobs=[${queue.size}]")
    cron.stop()
    
    // Clear queue - futures will continue running but won't be processed
    val n = queue.size
    queue = Queue.empty
    executor.shutdown()
  }

  private def process(): Unit = {
    val n = queue.size
    val now = System.currentTimeMillis()
    
    // Check for completed jobs and expired jobs
    val (completed, pending) = queue.partition { j =>
      j.future.isCompleted || 
      (now - j.ts0) > timeout0
    }

    log.debug(s"[process]: Jobs=[${n},${completed.size},${pending.size}]")
    
    completed.foreach { j =>
      val elapsed = now - j.ts0
      
      if (elapsed > timeout0) {
        // Job expired - just log it, future continues running
        log.warn(s"Job expired: ${j}: ${elapsed}ms (timeout=${timeout0}ms)")
      } else {
        // Job completed normally
        j.future.value match {
          case Some(Success(r)) =>
            log.info(s"Job success: ${r}")

            try {

              j.job.onSuccess(r)

            } catch {
              case e: Exception =>
                // Handle failed jobs                
                log.warn(s"Job failed: ${j}: ${e.getMessage}")
                j.job.onError(e)
            }
          case Some(Failure(e)) =>
            log.warn(s"Job Future failed: ${j}", e)
            j.job.onError(e)
            
          case None =>
            ;
        }
      }
    }
    
    queue = pending
  }
}
