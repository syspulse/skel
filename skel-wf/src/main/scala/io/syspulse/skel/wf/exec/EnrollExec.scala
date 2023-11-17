package io.syspulse.skel.wf.exec

import scala.util.{ Try, Failure, Success }
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.util.Util
import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.Executing

class EnrollPhaseStartExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) 
  extends Executing(wid,name,dataExec ++ Map()) {
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    val phaseId =  data.attr.get("enroll.pid").getOrElse("START").asInstanceOf[String]
    val phaseData = data.attr.get("enroll.data").getOrElse("").asInstanceOf[String]

    phaseId match {
      case "START" => 
        log.info(s"Phase: ${phaseId}")

        val name = data.attr.get("enroll.name").getOrElse("").asInstanceOf[String]
        val email = data.attr.get("enroll.email").getOrElse("").asInstanceOf[String]
        // remove data to make it clean
        val data1 = data.copy( attr = data.attr - ("enroll.data"))

        if(email.isBlank() || !email.contains('@')) {
          log.error(s"Invalid email: ${email}")
          return Success(ExecDataEvent(data1))
        } 

        val data2 = data.copy( attr = data.attr ++ Map("enroll.code" -> "1111", "enroll.pid" -> "CONFIRM"))

        broadcast(data2)
        Success(ExecDataEvent(data2))

      case _ => 
        // pass to the next phase
        broadcast(data)
        Success(ExecDataEvent(data))
    }
  }
}

class EnrollPhaseConfirmExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) 
  extends Executing(wid,name,dataExec ++ Map()) {
  
  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    val phaseId =  data.attr.get("enroll.pid").getOrElse("").asInstanceOf[String]
    val phaseData = data.attr.get("enroll.data").getOrElse("").asInstanceOf[String]

    phaseId match {
      case "CONFIRM" =>
        log.info(s"Phase: ${phaseId}")
        
        val code = data.attr.get("enroll.code").getOrElse("").asInstanceOf[String]
        val confirm = data.attr.get("enroll.confirm").getOrElse("").asInstanceOf[String]

        println(s"===========================> ${code}, ${confirm}")
        
        // remove data to make it clean
        val data1 = data.copy( attr = data.attr - ("enroll.data"))

        if(code != confirm) {
          log.error(s"Invalid Confirm Code: ${confirm}")
          return Success(ExecDataEvent(data1))
        } 

        broadcast(data1)
        Success(ExecDataEvent(data1))

      case "START" | "" => 
        log.error(s"Invalid phase: ${phaseId}")
        Success(ExecDataEvent(data))

      case _ =>         
        // pass to the next phase
        broadcast(data)
        Success(ExecDataEvent(data))
    }
  }
}


