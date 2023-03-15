package io.syspulse.skel.wf.runtime

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import scala.util.{Try,Success,Failure}

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

import io.syspulse.skel.wf.runtime._
import io.syspulse.skel.wf._

trait ExecEvent

sealed case class ExecDataEvent(data:ExecData) extends ExecEvent
sealed case class ExecCmdEvent(cmd:String,replyTo: ActorRef[ExecEvent]) extends ExecEvent
sealed case class ExecCmdStart(replyTo:ActorRef[ExecEvent]) extends ExecEvent
sealed case class ExecCmdStop(who:String) extends ExecEvent
sealed case class ExecCmdRunningReq(replyTo:ActorRef[ExecCmdRunningRes]) extends ExecEvent
sealed case class ExecCmdRunningRes(r:Running) extends ExecEvent

case class LinkAddr(exec:Executing,let:String)

case class Linking(from:LinkAddr,to:LinkAddr) {
  val log = Logger(s"${this}")

  var running:Option[Running] = None
  
  def bind(running:Running) = {
    this.running = Some(running)
  }

  def input(e: ExecEvent) = {
    log.debug(s"${e} ---> Running(${running})")
    running match {
      case Some(r) => r.!(e)
      case None => 
        log.warn(s"not bound Running: ${running}: ${e}")
    }
    
  }

  def output(e: ExecEvent) = {
    log.debug(s"${e} ---> Running(${running}) -> ${to.exec}")
    to.exec.onEvent(to.let,e)    
  }
}
