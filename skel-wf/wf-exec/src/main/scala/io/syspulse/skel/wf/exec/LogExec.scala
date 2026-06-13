package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.util.Util

class LogExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) { 
  
  // def logger: (String) => Unit = dataExec.getOrElse("log.level","INFO") match {
  //   case "WARN" => msg => log.warn(msg)
  //   case "INFO" => msg => log.info(msg)
  //   case "ERROR" => msg => log.error(msg)
  // }
  def logger(logLevel:String,msg:String) = logLevel.toUpperCase match {
    case "WARN" | "WARNING" => log.warn(s"${Console.YELLOW}${msg}${Console.RESET}")
    case "INFO" => log.info(s"${Console.BLUE}${msg}${Console.RESET}")
    case "ERR" | "ERROR" => log.error(s"${Console.RED}${msg}${Console.RESET}")
  }
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    //log.info(s"LOGGING: > ${dataExec.getOrElse("sys","*").toString.repeat(25)}: data.exec=${dataExec}: data.workflow=${dataWorkflow}: data=${data}")
    val logLevel = getAttr("log.level",data).getOrElse("INFO").asInstanceOf[String]
    
    logger(logLevel,s"${name}: In=${in}: data.exec=${dataExec}: data.workflow=${dataWorkflow}: data=${data}")
    
    broadcast(data)
    Success(ExecDataEvent(data))
  }
}
