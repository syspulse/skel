package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class ProcessExec(wid:Workflowing.ID,name:String) extends Executing(wid,name) {
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecData] = {
    val data1 = data.attr.get("script") match {
      case Some(script) => ExecData(attr = data.attr ++ Map("processed" -> System.currentTimeMillis.toString))
      case None => data
    }

    log.info(s"data1 = ${data1}")
    
    broadcast(data1)
    Success(data1)
  }
}
