package io.syspulse.skel.cron

import scala.concurrent.duration.Duration
import java.io.Closeable
import scala.concurrent.duration.FiniteDuration
import scala.util.{Try,Success,Failure}

object CronFreq {
  def parseHuman(freq: String): Long = {
    val pattern = """(\d+)\s*(ms|msec|millisecond|sec|second|min|minute|hour|day)s?""".r
    freq.toLowerCase match {
      case pattern(value, unit) => 
        val milliseconds = unit match {
          case "ms" | "msec" | "millisecond" | "milliseconds" => 1L
          case "sec" | "second" | "seconds" => 1000L
          case "min" | "minute" | "minutes" => 60000L
          case "hour" | "hours" => 3600000L
          case "day" | "days" => 86400000L
        }
        value.toLong * milliseconds
      case _ => 
        freq.toLong
    }
  }

  def toMillis(expr: String): Long = {
    CronFreq.parseHuman(expr)
  }
}

// Simple Frequncy ticker
class CronFreq(runner: (Long)=>Boolean, freq:String, delay0:Long = 250L, limit:Long = 0L) extends Cron[Unit] {
    
  import java.util.concurrent._
  protected val cronScheduler = new ScheduledThreadPoolExecutor(1)
  var count = 0L
  @volatile
  protected var cronFuture: Option[ScheduledFuture[_]] = None
  
  override def toMillis: Long = interval

  val (interval:Long,delay:Long) = {
    freq.split("\\/").toList match {
      case interval :: delay :: _ => (CronFreq.parseHuman(interval),delay.toLong)
      case interval :: Nil => (CronFreq.parseHuman(interval),delay0)
      case _ => (1000L,delay0)
    }
  }
  
  def getExpr():String = freq

  def start():Try[Unit] = {    
    if(cronFuture.isDefined) cronFuture.get.cancel(true)
    val task = new Runnable {
      var ts0 = System.currentTimeMillis()
      def run() = {
        val now = System.currentTimeMillis()
        runner(now - ts0)
        ts0 = now
      }
    }
    if(limit == 0L || count < limit) {
      val dur = FiniteDuration(interval,TimeUnit.MILLISECONDS)
      cronFuture = Some(cronScheduler.scheduleAtFixedRate(task, delay, dur.length, dur.unit))
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
