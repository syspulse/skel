package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class VarExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  val varName = dataExec.get("var.name")

  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    val data1 = if(varName.isDefined) {
      val inputs = getAttr("input",data).getOrElse("").asInstanceOf[String]
            
      val data1 = ExecData(data.attr + (varName.get.asInstanceOf[String] -> inputs))

      broadcast( data1 )
      data1

    } else {
      broadcast( data ) 
      data
    }

    Success(ExecDataEvent(data1))
  }
}
