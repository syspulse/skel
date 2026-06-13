package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class ThrottleExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {    
    val throttle:Long = getAttr("throttle.delay",data).getOrElse("1000").toString.toLong

    log.info(s"throttling: ${throttle}...")
    
    Thread.sleep(throttle)
    
    broadcast(data)
    Success(ExecDataEvent(data))
  }
}
