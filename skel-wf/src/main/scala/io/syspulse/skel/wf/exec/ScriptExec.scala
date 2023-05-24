package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.dsl.ScalaToolbox

import ujson._

class ScriptExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  
  val engine = new ScalaToolbox()
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    val r = getAttr("script",data) match {
      case Some(script) =>         
        log.info(s"script='${script}'")
        val src = if(script.toString.startsWith("file://")) 
          os.read(os.Path(script.toString.stripPrefix("file://"),os.pwd))
        else
          script.toString

        val output = try {
          val output = engine.run(src,data.attr ++ dataExec)
          val data1 = ExecData(data.attr + ("input" -> output))
          broadcast(data1)
          Success(ExecDataEvent(data1))

        } catch {
          case e:Throwable => 
            log.error(s"script failed",e)
            val data1 = ExecData(data.attr + ("input" -> e.getMessage()))
            send("err-0",data1)
            Failure(e)
        }
        output
        
      case _ => 
        broadcast(data)
        Success(ExecDataEvent(data))
    }

    log.info(s"r = ${r}")
    r
  }
}
