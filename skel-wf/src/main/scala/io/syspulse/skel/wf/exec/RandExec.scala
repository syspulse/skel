package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import scala.util.Random

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class RandExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {    
    val randNum:Int = getAttr("rand.num",data).getOrElse(3).toString.toInt

    log.info(s"randoming: ${randNum}")
    
    val data1 = ExecData(data.attr + ("input" -> Range(0,randNum).map(_ => Random.nextLong()).toList))
        
    broadcast(data1)

    Success(ExecDataEvent(data1))
  }
}
