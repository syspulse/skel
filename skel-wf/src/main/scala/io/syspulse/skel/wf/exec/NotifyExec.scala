package io.syspulse.skel.wf.exec

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._
import io.syspulse.skel.util.Util
import io.syspulse.skel.notify.client._

class NotifyExec(wid:Workflowing.ID,name:String,dataExec:Map[String,Any]) extends Executing(wid,name,dataExec) {   
  val serviceUri = dataExec.get("notify.uri").getOrElse("http://localhost:8080/api/v1/notify").asInstanceOf[String]
  val timeout = FiniteDuration(10,TimeUnit.SECONDS)

  override def exec(in:Let.ID,data:ExecData):Try[ExecEvent] = {
    var notifyTarget = dataExec.get("notify").getOrElse("stdout://").asInstanceOf[String]
        
    import io.syspulse.skel.FutureAwaitable._                
          
    val (to:String,subj:String,msg:String,severity:Int,scope:String) = notifyTarget.split("\\s+").toList match {
      case to :: subj :: msg :: severity :: scope :: Nil => (to,subj,msg,severity.toInt,scope)
      case to :: subj :: msg :: severity :: Nil => (to,subj,msg,severity.toInt,"sys.all")
      case to :: subj :: msg :: Nil => (to,subj,msg,1,"sys.all")
      case to :: subj :: Nil => (to,subj,"",1,"sys.all")
      case to :: Nil => (to,"","",1,"sys.all")
      case Nil => ("stdout://","","",1,"sys.all")
      case _ => (notifyTarget.take(notifyTarget.size-2).mkString(" "),notifyTarget.takeRight(2).head,notifyTarget.last)
    }

    log.info(s"Notify(${to},${subj},${msg},${severity},${scope}) -> ${serviceUri}")
                    
    val r = NotifyClientHttp(serviceUri)
        .withTimeout(this.timeout)
        .notify(to,subj,msg,Some(severity),Some(scope))
        .await()
                  
    broadcast(data)
    Success(ExecDataEvent(data))
  }
}
