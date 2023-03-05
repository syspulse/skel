package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class TerminateExec(wid:Workflowing.ID,name:String) extends Executing(wid,name) {

  def exec(data:ExecData):Try[ExecData] = {
    log.warn(s"terminating=${data}")
    Failure(new Exception(s"Terminated"))
  }
}
