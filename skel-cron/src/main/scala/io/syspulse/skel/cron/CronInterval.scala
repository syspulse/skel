package io.syspulse.skel.cron

import scala.concurrent.duration.Duration
import java.io.Closeable

// Legacy to be removed
class CronInterval(interval:Duration, runner: ()=>Unit, delay:Long = 0L, limit:Long = 0L) extends Closeable {
  import java.util.concurrent._
  protected val cronScheduler = new ScheduledThreadPoolExecutor(1)
  var count = 0L
  @volatile
  protected var cronFuture: Option[ScheduledFuture[_]] = None

  // Start immediately
  start

  def start = {    
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
}
