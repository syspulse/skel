package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

class JoinExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  val joiner = dataExec.get("join.symbol").getOrElse("").asInstanceOf[String] match {
    case "\\n" => "\n"
    case j => j
  }
  var collected:List[Any] = List()

  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {    
    val max = getAttr("join.max",data,10).asInstanceOf[Int]
    
    val input = data.attr.get("input")
    input match {
      case Some(v) => 
        collected = collected :+ v
      case None =>
    }

    // log.debug(s"max(${max}): collected=${collected}")
        
    if(collected.size == max) {
      log.debug(s"max(${max}): collected=${collected}")

      val output = input match {
        case Some(v) => 
          collected.map(_.toString).mkString(joiner)
        case None =>
          ""
      }

      val data1 = ExecData(data.attr + ("input" -> output))
      collected = List()
      broadcast(data1) 

      Success(ExecDataEvent(data1))
    } else {
      Success(ExecDataEvent(data))
    }
  }
}
