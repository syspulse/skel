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

case class LinkAddr(exec:Executing,let:String)

case class Linking(wid:Workflowing.ID,from:LinkAddr,to:LinkAddr) {

  @volatile
  var actor:Option[ActorRef[ExecEvent]] = None
  
  def init(actor:Option[ActorRef[ExecEvent]]) = this.actor = actor

  def !(e: ExecEvent) = actor.map(_ ! e)
}


object Linking {
  val log = Logger(s"${this}")

  def actor(link:Linking): Behavior[ExecEvent] = {
    Behaviors.receiveMessage {
      case ExecCmdEvent(cmd,replyTo) =>
        log.info(s"cmd=${cmd}")
        Behaviors.same
      case ExecDataEvent(data) =>
        log.info(s"data=${data}")
        link.to.exec.onEvent(data)
        Behaviors.same
    }
  }
}

