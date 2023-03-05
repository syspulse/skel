package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.util.Util

class LogExec(wid:Workflowing.ID,name:String) extends Executing(wid,name) {
  override def exec(in:Let.ID,data:ExecData):Try[ExecData] = {
    log.info(s"LOGGING: > data=${data}")
    
    val data1 = ExecData(data.attr ++ Map(s"log.ts.${System.currentTimeMillis}" -> Util.sha256(data.attr.toString)))
    broadcast(data1)
    Success(data1)
  }
}
