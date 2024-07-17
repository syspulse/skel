package io.syspulse.skel.cron

import scala.concurrent.duration.Duration
import java.io.Closeable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Try,Success,Failure}

// Simple Frequncy ticker
class CronFreq(runner: (Long)=>Boolean, freq:String, delay0:Long = -1L, limit:Long = 0L) extends Cron[Unit] {
  
  // def `this`(runner: (Long)=>Boolean, freq:Duration, delay:Long, limit:Long) = {
  //   interval = freq
  // }

  import java.util.concurrent._
  protected val cronScheduler = new ScheduledThreadPoolExecutor(1)
  var count = 0L
  @volatile
  protected var cronFuture: Option[ScheduledFuture[_]] = None

  val interval:Duration = {
    val interval = freq.split("\\/").toList match {
      case interval :: delay :: _ => interval     
      //case interval :: delay :: Nil => interval
      case interval :: Nil => interval      
    }
    
    //Duration(interval)
    FiniteDuration(interval.toLong,TimeUnit.MILLISECONDS)
  }

  val delay = if(freq.contains("/")) {
    freq.split("/").last.toLong
  } else 
  if(delay0 == -1)
    interval.toMillis
  else
    delay0

  def start():Try[Unit] = {    
    if(cronFuture.isDefined) cronFuture.get.cancel(true)
    val task = new Runnable { 
      def run() = runner(0L)
    }
    if(limit == 0L || count < limit) {
      cronFuture = Some(cronScheduler.scheduleAtFixedRate(task, delay, interval.length, interval.unit))
      count = count + 1
    }
    Success(())
  }

  def stop() = {
    cronScheduler.shutdown()
  }

  override def close = {
    if(cronFuture.isDefined) cronFuture.get.cancel(true)
  }

  // Start immediately
  // start()
}
