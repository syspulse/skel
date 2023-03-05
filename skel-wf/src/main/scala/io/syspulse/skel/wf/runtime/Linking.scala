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
sealed case class ExecCmdStop() extends ExecEvent

case class LinkAddr(exec:Executing,let:String)

case class Linking(wid:Workflowing.ID,from:LinkAddr,to:LinkAddr) {

  def !(e: ExecEvent) = to.exec.onEvent(e)
}


// object Linking {
//   val log = Logger(s"${this}")

//   def actor(link:Linking): Behavior[ExecEvent] = {
//     Behaviors.receiveMessage { 
//       case ExecCmdEvent(cmd,replyTo) =>
//         log.info(s"cmd=${cmd}")
//         Behaviors.same
//       case event @ ExecDataEvent(data) =>
//         log.info(s"data=${data}")
//         link.!(event)
//         Behaviors.same
//     }
//   }
// }

