package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class ThrottleExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  val throttle:Long = dataExec.getOrElse("throttle",1000L).toString.toLong
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {    
    log.info(s"throttling: ${throttle}...")
    
    Thread.sleep(throttle)
    
    broadcast(data)
    Success(ExecDataEvent(data))
  }
}
