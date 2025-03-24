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
  def getExpr():String

  def toMillis:Long
  
}

object Cron {
  def apply(exec:(Long)=>Boolean, expr:String, settings:Map[String,Any] = Map(),rateLimit:Option[Long]=None): Cron[_] = {
    if(expr.contains("*") || expr.contains("_")) {
      val conf = settings.get("conf").asInstanceOf[Option[(String,Configuration)]]
      val cronName = settings.get("cronName").asInstanceOf[Option[String]].getOrElse("cron-1")
      val jobName:String=settings.get("jobName").asInstanceOf[Option[String]].getOrElse("job-1")
      val groupName:String=settings.get("groupName").asInstanceOf[Option[String]].getOrElse("group-1")
      
      if(rateLimit.isDefined && CronQuartz.toMillis(expr) <= rateLimit.get)
        new CronFreq(exec,rateLimit.get.toString,rateLimit.get)
      else
        new CronQuartz(exec,expr,conf = conf,cronName,jobName,groupName)
      
    } else {
      val delay = settings.get("delay")
        .asInstanceOf[Option[String]]
        .filter(_.nonEmpty)
        .map(CronFreq.toMillis(_))
        .getOrElse(-1L)

      val expression = if(rateLimit.isDefined && CronFreq.toMillis(expr) <= rateLimit.get)
        rateLimit.get.toString
      else
        expr
      new CronFreq(exec,expression,delay)
    }
  }   
}

