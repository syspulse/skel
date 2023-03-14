package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class TerminateExec(wid:Workflowing.ID,name:String) extends Executing(wid,name) {

  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    log.warn(s"TERMINATING(${data})")
    //Failure(new Exception(s"Terminated"))
    Success(ExecCmdStop(name))
  }
}
