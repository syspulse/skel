package io.syspulse.skel.wf.registry

//import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime._

class WorkflowRegistry(execs0:Seq[Exec] = Seq()) {
  val log = Logger(s"${this}")
  
  val execs:Map[Exec.ID,Exec] = (WorkflowRegistry.default ++ execs0).map(f => f.name -> f).toMap  
  
  log.info(s"execs: ${execs.values.toSeq.map(_.name).mkString(",")}")

  def resolve(name:String): Option[Exec] = {
    execs.get(name).orElse(execs.values.find(_.typ == name).headOption)
  }
}

object WorkflowRegistry {

  def fromString(reg:Seq[String]):Seq[Exec] = {
    reg.flatMap(_.split("=").toList match {
      case name :: cl :: _ => Some(Exec(name,cl))
      case _ => None
    })
  }

  val default = Seq(
    Exec("Log","io.syspulse.skel.wf.exec.LogExec"),
    Exec("LogExec","io.syspulse.skel.wf.exec.LogExec"),

    Exec("Process","io.syspulse.skel.wf.exec.ProcessExec"),

    Exec("Terminate","io.syspulse.skel.wf.exec.TerminateExec"),
    Exec("TerminateExec","io.syspulse.skel.wf.exec.TerminateExec"),

    Exec("Cron","io.syspulse.skel.wf.exec.CronExec"),
    Exec("Throttle","io.syspulse.skel.wf.exec.ThrottleExec"),    
    Exec("Rand","io.syspulse.skel.wf.exec.RandExec"),
    Exec("Seq","io.syspulse.skel.wf.exec.SeqExec"),
    Exec("Coll","io.syspulse.skel.wf.exec.CollExec"),
    Exec("Notify","io.syspulse.skel.wf.exec.NotifyExec"),

    Exec("Fifo","io.syspulse.skel.wf.exec.FifoExec"),

    Exec("HTTP","io.syspulse.skel.wf.exec.HttpClientExec"),
    Exec("Script","io.syspulse.skel.wf.exec.ScriptExec"),
    Exec("Split","io.syspulse.skel.wf.exec.SplitExec"),
    Exec("FileRead","io.syspulse.skel.wf.exec.FileReadExec"),
    Exec("FileWrite","io.syspulse.skel.wf.exec.FileWriteExec"),

    Exec("Join","io.syspulse.skel.wf.exec.JoinExec"),

    Exec("Var","io.syspulse.skel.wf.exec.VarExec"),

    Exec("Server","io.syspulse.skel.wf.exec.HttpServerExec"),
    //Exec("ServerCommand","io.syspulse.skel.wf.exec.HttpServerCommandExec"),

    Exec("EnrollStart","io.syspulse.skel.wf.exec.EnrollPhaseStartExec"),
    Exec("EnrollConfirm","io.syspulse.skel.wf.exec.EnrollPhaseConfirmExec"),
  )
}
