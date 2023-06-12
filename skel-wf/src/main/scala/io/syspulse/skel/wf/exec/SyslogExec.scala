package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

// SyslogExec expects to have 2 inputs 
// -> [in-0] - will send event to skel-notify and will no continue to the flow
// -> [in-1] - expects external event to continue. Filter will control if this is expected event (e.g. from kafka://)
class SyslogExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  var filter = dataExec.get("syslog.filter").getOrElse("").asInstanceOf[String]
  var notifySyslog = dataExec.get("syslog.notify")
  var listenSystlog = dataExec.get("syslog.listen")
    
  override def start(dataWorkflow:ExecData):Try[Status] = {    
    super.start(dataWorkflow)
  }

  override def stop():Try[Status] = {
    super.stop()
  }

  
  // push to FIFO file on input
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {      
    val d = data.attr.get("input").getOrElse("").toString
    log.info(s"${d} ->> Syslog[${notifySyslog}]")
       
    Success(ExecDataEvent(data))  
  }
}
