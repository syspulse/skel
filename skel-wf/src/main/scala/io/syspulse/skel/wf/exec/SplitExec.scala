package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class SplitExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  val empty = dataExec.get("split.empty").getOrElse("false").asInstanceOf[String].toBoolean
  val splitter = dataExec.get("split.symbol").getOrElse("\n").asInstanceOf[String]
    
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    val splitMax = getAttr("split.max",data,-1).asInstanceOf[Int]
    val inputs = getAttr("input",data).getOrElse("").asInstanceOf[String]
    
    log.info(s"input=${inputs}")
    val output = {
      val ii1 = inputs
        .split(splitter)
        .filter(s => 
          empty ||
          (!s.trim.isEmpty() && !(splitter=="\n" && (s.trim == "\r")))
        )      
      if(splitMax > 0) ii1.take(splitMax) else ii1     
    } 

    val data1 = data.attr + ("input.size" -> output.size)

    log.info(s"output=${output}")

    // this is async
    for (out <- output) {
      // override input with new value
      val data2 = data1 + ("input" -> out)
      broadcast( ExecData(data2) ) 
    }
    Success(ExecDataEvent(ExecData(data1 + ("input"->output.mkString(",")))))
  }
}
