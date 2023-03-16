package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class ProcessExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    val data1 = getAttr("script",data) match {
      case Some(script) => 
        // replace {} with variables from Data
        val cmd = (data.attr ++ dataExec).foldLeft(script.toString){ case(s,(name,v)) => {
          s.replaceAll(s"\\{${name}\\}",v.toString) 
        }}
        
        val err = new StringBuffer()
        val r = cmd lazyLines_! ProcessLogger(err append _)
        val txt = r.mkString("\n")
        log.info(s"cmd = '${cmd}' -> '${txt}'")
        ExecData(data.attr + ("output" -> txt))
      case None => 
        data
    }
    
    broadcast(data1)
    Success(ExecDataEvent(data1))
  }
}
