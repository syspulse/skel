package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class ProcessExec(wid:Workflowing.ID,name:String) extends Executing(wid,name) {
  
  def exec(data:ExecData):Try[ExecData] = {
    val data1 = data.attr("script") match {
      case Some(script) => ExecData(attr = data.attr ++ Map("processed" -> System.currentTimeMillis.toString))
      case None => data
    }

    Success(data1)
  }
}
