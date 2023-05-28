package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.dsl.ScalaToolbox

import ujson._

class FileReadExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    val r = getAttr("file",data) match {
      case Some(f) =>         
        log.info(s"file='${f}'")
                          
        val r = try {
          val output = f.toString.split("//").toList match {
            case "file" :: name :: Nil =>
              os.read(os.Path(name,os.pwd))
            case name :: Nil => 
              os.read(os.Path(name,os.pwd))
          }

          val data1 = ExecData(data.attr + ("input" -> output))
          broadcast(data1)
          Success(ExecDataEvent(data1))

        } catch {
          case e:Throwable => 
            log.error(s"failed to read file: ${f}",e)
            val data1 = ExecData(data.attr + ("input" -> e.getMessage()))
            send("err-0",data1)
            Failure(e)
        }

        r
        
      case _ => 
        broadcast(data)
        Success(ExecDataEvent(data))
    }

    log.info(s"r = ${r}")
    r
  }
}
