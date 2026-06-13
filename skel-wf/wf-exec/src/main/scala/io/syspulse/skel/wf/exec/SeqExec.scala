package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class SeqExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  
  // ATTENTION: Expected List[] in the 'input' !
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    // log.debug(s"Sequence: ${data}")
    val units = getAttr("input",data).getOrElse(List()).asInstanceOf[List[_]]

    log.debug(s"units=${units}")
    val data1 = data.attr + ("seq.size" -> units.size)

    // this is async
    for (u <- units) {
      // override input with new value
      val data2 = data1 + ("input" -> u)
      broadcast( ExecData(data2) ) 
    }
    Success(ExecDataEvent(data))
  }
}
