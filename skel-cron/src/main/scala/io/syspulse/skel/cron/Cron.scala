package io.syspulse.skel.cron

import scala.util.{Try,Success,Failure}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import java.util.concurrent._
import java.io.Closeable

import com.typesafe.scalalogging.Logger

import org.quartz.Scheduler;
import org.quartz.SchedulerException
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{ JobExecutionException, Job, JobDetail, JobExecutionContext, JobDataMap }
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.SimpleScheduleBuilder._

import org.quartz.CronScheduleBuilder._
import org.quartz.DateBuilder._

import io.syspulse.skel.config.Configuration

trait Cron[T] extends Closeable {
  def start():Try[T]
  def stop():Unit
}

object Cron {
  def apply(exec:(Long)=>Boolean, expr:String, settings:Map[String,Any] = Map()): Cron[_] = {
    if(expr.contains("*") || expr.contains("_")) {
      val conf = settings.get("conf").asInstanceOf[Option[(String,Configuration)]]
      val cronName = settings.get("cronName").asInstanceOf[Option[String]].getOrElse("Cron1")
      val jobName:String=settings.get("jobName").asInstanceOf[Option[String]].getOrElse("job1")
      val groupName:String=settings.get("groupName").asInstanceOf[Option[String]].getOrElse("group1")      
      
      new CronQuartz(exec,expr.replaceAll("_"," "),conf = conf,cronName,jobName,groupName)
    } else {
      val delay = settings.get("delay").asInstanceOf[Option[Long]].getOrElse(-1L)
      new CronFreq(exec,expr,delay)
    }
  }
    
}

