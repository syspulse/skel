package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class LogExec(wid:Workflowing.ID,name:String) extends Flowing(wid,name) {

  def exec(data:FlowingData):Try[FlowingData] = {
    log.warn(s"data=${data}")
    Success(data)
  }
}
