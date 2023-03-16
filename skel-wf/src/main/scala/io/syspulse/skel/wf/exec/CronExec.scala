package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.util.Util

class CronExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  val cronExpr = dataExec.getOrElse("cron","1000").toString

  @volatile
  var terminated = false
  
  val thr = new Thread() {
    override def run() = {
      log.info(s"CronExec: ${cronExpr}: running...")
      while( !terminated ) {
        Thread.sleep(cronExpr.toLong)

        broadcast(ExecData(dataExec))
      }        
    }
  }
  
  override def stop():Try[Status] = {
    terminated = true
    thr.interrupt()
    super.stop()
  }

  override def start(dataWorkflow: ExecData): Try[Status] = {
    val s = super.start(dataWorkflow)
    thr.start()
    s
  }
}
