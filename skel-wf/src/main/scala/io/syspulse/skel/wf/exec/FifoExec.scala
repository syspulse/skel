package io.syspulse.skel.wf.exec

import scala.sys.process._

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

import java.io._
import os._

// Fifo pipe (simulator of Kafka -> [Topic] -> )

class FifoExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {
  val file = dataExec.get("fifo.file").getOrElse("FIFO").asInstanceOf[String]

  val NOT_INITIALIZED = Failure(new Exception("not initialized"))
  var rafOutput:Try[RandomAccessFile] = NOT_INITIALIZED
  var rafInput:Try[RandomAccessFile] = NOT_INITIALIZED

  @volatile
  var terminated = false
  
  val thr = new Thread() {
    override def run() = {
      log.info(s"Fifo[${file}]: listening...")
      while( !terminated ) {

        val d = rafInput.get.readLine()
        
        log.info(s"${d} <<- Fifo[${file}]")

        broadcast(ExecData(Map("input" -> d)))
      }      
    }
  }

  override def start(dataWorkflow:ExecData):Try[Status] = {    
    rafOutput = Try(new RandomAccessFile(new File(file),"rw"))
    rafInput = Try(new RandomAccessFile(new File(file),"rw"))
    
    thr.start()
    
    super.start(dataWorkflow)
  }

  override def stop():Try[Status] = {
    
    rafOutput.map(_.close())
    rafInput.map(_.close())

    terminated = true
    thr.interrupt()
    super.stop()
  }

  
  // push to FIFO file on input
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {      
    val d = data.attr.get("input").getOrElse("").toString
    log.info(s"${d} ->> Fifo[${file}]")
    
    rafOutput.get.writeChars(d)
    
    Success(ExecDataEvent(data))  
  }
}
