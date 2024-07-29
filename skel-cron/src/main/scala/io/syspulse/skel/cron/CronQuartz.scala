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

object CronQuartz {
  val DATA_KEY = "cronjob-data"
}

case class CronQuartzJobData(exec:(Long)=>Boolean,var ts0:Long)

class CronQuartzJob extends Job {
	def execute(context:JobExecutionContext) = {
    val data: JobDataMap = context.getMergedJobDataMap();
    
    val cd = data.get(CronQuartz.DATA_KEY).asInstanceOf[CronQuartzJobData]
    val ts0: Long = cd.ts0

    val ts1 = System.currentTimeMillis
		if(false == cd.exec(ts1 - ts0)) 
      throw new JobExecutionException
    cd.ts0 = ts1
	}
}

class CronQuartz(exec:(Long)=>Boolean, expr:String, conf:Option[(String,Configuration)] = None, cronName:String="Cron1",jobName:String="job1",groupName:String="group1") 
  extends Cron[java.time.LocalDate] {
  
  val log = Logger(s"${this}")

  log.info(s"expr='${expr}': ${cronName},${jobName},${groupName}")

  // set default 1 thread
  if(System.getProperty("org.quartz.threadPool.threadCount") == null) System.setProperty("org.quartz.threadPool.threadCount","1")

  lazy val scheduler = 
    if(conf.isDefined) {
      // load config from config branch
      val (configName,configuration) = conf.get

      val prefix = if(configName.isBlank()) "" else configName.trim + "."

      val pp = new java.util.Properties()
      configuration.getAll().foreach{ case(k,v) => {
        if(k.startsWith(s"${prefix}org.quartz.")) {
          val key = k.stripPrefix(prefix)
          log.debug(s"setting: ${key}=${v}")
          pp.put(key,v.toString)
        }
      }}

      val sf = new StdSchedulerFactory()
      log.info(s"initializing Quartz: Properties(${pp})")
      sf.initialize(pp)
      sf.getScheduler
    } else {
      StdSchedulerFactory.getDefaultScheduler()
    }

  def getExpr():String = expr

  def start():Try[java.time.LocalDate] = {
    try {
      scheduler.start();

      val job:JobDetail = newJob(classOf[CronQuartzJob])
      .withIdentity(jobName, groupName)
      .usingJobData(new JobDataMap(Map(CronQuartz.DATA_KEY -> CronQuartzJobData(exec,System.currentTimeMillis)).asJava))
      .build();

      val trigger = newTrigger()
        .withIdentity(cronName, groupName)
        .withSchedule(cronSchedule(expr))
        .forJob(jobName, groupName)
        .build()

      val date = scheduler.scheduleJob(job, trigger)
      Success(date.toInstant.atZone(java.time.ZoneId.systemDefault).toLocalDate)

    } catch {
      case e: Exception => Failure(e)
    }
  }

  def stop() = {
    scheduler.shutdown()
  } 

  override def close = {
    scheduler.shutdown();
  }
}

