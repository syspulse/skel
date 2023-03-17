package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class CollExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  
  var collected:List[Any] = List()

  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {    
    val max = getAttr("collect.max",data).getOrElse(10).asInstanceOf[Int]
    
    data.attr.get("input") match {
      case Some(v) => 
        collected = collected :+ v
      case None =>
    }

    log.debug(s"max(${max}): collected=${collected}")
        
    if(collected.size == max) {
      val data1 = ExecData(data.attr + ("input" -> collected))
      collected = List()
      broadcast(data1) 

      Success(ExecDataEvent(data1))
    } else {
      Success(ExecDataEvent(data))
    }
  }
}
