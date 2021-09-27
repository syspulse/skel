package io.syspulse.skel.cron

import scala.util.{Try,Success,Failure}
import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._
import java.util.concurrent._
import java.io.Closeable

import org.quartz.Scheduler;
import org.quartz.SchedulerException
import org.quartz.impl.StdSchedulerFactory
import org.quartz.{ Job, JobDetail, JobExecutionContext, JobDataMap }
import org.quartz.JobBuilder._
import org.quartz.TriggerBuilder._
import org.quartz.SimpleScheduleBuilder._

import org.quartz.CronScheduleBuilder._
import org.quartz.DateBuilder._

object Cron {
  val EXEC_KEY = "exec"
}

case class CronJobData(exec:(Long)=>Boolean)
class CronJob extends Job {
  
	def execute(context:JobExecutionContext) = {

    // throws JobExecutionException
		val data: JobDataMap = context.getMergedJobDataMap();
    val cd = data.get(Cron.EXEC_KEY).asInstanceOf[CronJobData]
		//System.out.println("someProp = " + data.getString("someProp"));
    cd.exec(0L)
	}

}

class Cron(exec:(Long)=>Boolean, expr:String,cronName:String="Cron1",jobName:String="job1",groupName:String="group1") extends Closeable {
  if(System.getProperty("org.quartz.threadPool.threadCount") == null) System.setProperty("org.quartz.threadPool.threadCount","1")

  lazy val scheduler = StdSchedulerFactory.getDefaultScheduler()

  def start:Try[java.time.LocalDate] = {
    try {
      scheduler.start();

      val job:JobDetail = newJob(classOf[CronJob])
      .withIdentity("job1", "group1")
      .usingJobData(new JobDataMap(Map(Cron.EXEC_KEY->CronJobData(exec)).asJava))
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

  def stop = {
    scheduler.shutdown()
  } 

  override def close = {
    scheduler.shutdown();
  }
}

