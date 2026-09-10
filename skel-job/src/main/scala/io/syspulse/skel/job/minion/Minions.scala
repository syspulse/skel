package io.syspulse.skel.job.minion

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import scala.concurrent.{Future, Promise}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import scala.concurrent.ExecutionContext
import scala.collection.immutable.Queue
import java.util.concurrent.{Executors, RejectedExecutionException, Future => JFuture}

import io.syspulse.skel.cron.Cron
import io.syspulse.skel.util.Util

// =========================================================================================================
trait Minion[J,R] {
  def run():R
  def onError(e:Throwable):Unit
  def onSuccess(r:R):Unit
}

object JobMaster {
  object State {
    val IDLE = "IDLE"
    val STARTED = "STARTED"
    val STOPPED = "STOPPED"
    val TERMINATED = "TERMINATED"
  }
}

class JobMaster[J,R](freq:String = "1000", threads:Int = 2, timeout0:Long = 1 * 60 * 1000L,max:Long = 10L) {
  val log = Logger(this.getClass)

  private val executor = Executors.newFixedThreadPool(threads)
  implicit val ex: ExecutionContext = ExecutionContext.fromExecutor(executor)
  
  case class JobRun(job:Minion[J,R], future:Future[R], running:JFuture[_], ts0:Long = System.currentTimeMillis())

  @volatile
  var queue:Queue[JobRun] = Queue.empty

  @volatile
  var state:String = JobMaster.State.IDLE
  
  val cron = Cron(
    (_) => {
      process()
      true
    },
    s"${freq}"
  )

  private def cannotRun(why: String): Future[R] = {
    log.warn(s"${why}")
    Future.failed(new Exception(why))
  }
  
  def +(job:Minion[J,R]):Future[R] = this.synchronized {
    if (state == JobMaster.State.STOPPED || state == JobMaster.State.TERMINATED) {
      return cannotRun(s"JobMaster ${state}: cannot run jobs")
    }
    if (queue.size >= max) {
      log.warn(s"Job queue full: ${queue.size} (max=${max})")
      return Future.failed(new Exception(s"Job queue full: ${queue.size}"))
    }

    val p = Promise[R]()
    val running = try {
      executor.submit(new Runnable {
        def run(): Unit = {
          try p.trySuccess(job.run())
          catch {
            case e: InterruptedException =>
              p.tryFailure(e)
              Thread.currentThread().interrupt()
            case e: Throwable =>
              p.tryFailure(e)
          }
        }
      })
    } catch {
      case e: RejectedExecutionException =>
        return cannotRun(s"JobMaster ${state}: cannot run jobs")
    }

    val jr = JobRun(job, p.future, running)
    queue = queue.+:(jr)
    
    log.info(s"add: ${jr} -> Jobs(${queue.size})")
    p.future
  }

  def start(): Unit = this.synchronized {
    if (state == JobMaster.State.TERMINATED) {
      log.warn(s"[start]: already terminated: ${cron}: executor=${executor}")
      return
    }
    log.info(s"[start]: ${cron}: ${freq}ms, executor=${executor}")
    cron.start()
    state = JobMaster.State.STARTED
  }

  def stop(): Unit = this.synchronized {
    if (state == JobMaster.State.TERMINATED) return
    log.info(s"[stop]: ${cron}: Jobs=[${queue.size}], executor=${executor}")
    state = JobMaster.State.STOPPED
    cron.stop()

    // interrupt in-flight work and drop the queue so process() cannot callback
    val n = queue.size
    queue.foreach(_.running.cancel(true))
    queue = Queue.empty
    log.info(s"[stop]: ${cron}: Queue=[${n}], executor=${executor}")
  }

  def terminate(): Unit = this.synchronized {
    log.info(s"[terminate]: ${cron}: Jobs=[${queue.size}], executor=${executor}")
    this.stop()
    try cron.terminate() catch { case _: Exception => () }
    executor.shutdownNow()
    state = JobMaster.State.TERMINATED
  }

  private def process(): Unit = this.synchronized {
    if (state != JobMaster.State.STARTED) return

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
