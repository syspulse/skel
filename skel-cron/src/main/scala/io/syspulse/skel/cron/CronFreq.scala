package io.syspulse.skel.cron

import scala.concurrent.duration.Duration
import java.io.Closeable

// Simple Frequncy ticker
class CronFreq(runner: ()=>Boolean, interval:Duration, delay:Long = 0L, limit:Long = 0L) extends Closeable {
  import java.util.concurrent._
  protected val cronScheduler = new ScheduledThreadPoolExecutor(1)
  var count = 0L
  @volatile
  protected var cronFuture: Option[ScheduledFuture[_]] = None

  def start() = {    
    if(cronFuture.isDefined) cronFuture.get.cancel(true)
    val task = new Runnable { 
      def run() = runner()
    }
    if(limit == 0L || count < limit) {
      cronFuture = Some(cronScheduler.scheduleAtFixedRate(task, delay, interval.length, interval.unit))
      count = count + 1
    }
  }

  override def close = {
    if(cronFuture.isDefined) cronFuture.get.cancel(true)
  }

  // Start immediately
  // start()
}
