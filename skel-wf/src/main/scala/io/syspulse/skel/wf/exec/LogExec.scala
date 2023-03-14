package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.util.Util

class LogExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    log.info(s"LOGGING: > ${dataExec.getOrElse("sys","*").toString.repeat(25)}: data.exec=${dataExec}: data.workflow=${dataWorkflow}: data=${data}")
    
    val data1 = ExecData(data.attr ++ Map(s"log.ts.${System.currentTimeMillis}" -> Util.sha256(data.attr.toString)))
    broadcast(data1)
    Success(ExecDataEvent(data1))
  }
}
