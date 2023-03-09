package io.syspulse.skel.wf.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime.ExecData
import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.Executing

class WorkflowStateStoreMem extends WorkflowStateStore {
  val log = Logger(s"${this}")
  
  var states: Map[Workflowing.ID,WorkflowState] = Map()

  def all:Seq[WorkflowState] = states.values.toSeq

  def size:Long = states.size

  def +(ws:WorkflowState):Try[WorkflowStateStore] = { 
    states = states + (ws.wid -> ws)
    log.info(s"add: ${ws}")
    Success(this)
  }

  def del(id:Workflowing.ID):Try[WorkflowStateStore] = { 
    val sz = states.size
    states = states - id;
    log.info(s"del: ${id}")
    if(sz == states.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:Workflowing.ID):Try[WorkflowState] = states.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }
 
  def update(id:Workflowing.ID, states:Option[Seq[State]] = None, events:Option[Long] = None):Try[WorkflowState] = 
    this.?(id) match {
      case Success(ws) => 
        val ws1 = modify(ws,states,events)
        this.+(ws1)
        Success(ws1)
      case f => f
    }

  def commit(id:Workflowing.ID,eid:Executing.ID,data:ExecData,status:Option[String]):Try[WorkflowState] = {
    ?(id) match {
      case Success(ws) => 
        val ws1 = ws.copy(states = ws.states :+ State(System.currentTimeMillis(),eid,data,status), count = ws.count + 1)
        this.+(ws1)
        log.debug(s"commiting: ${id}:${eid}: ${data}/${status}")
        Success(ws1)
      case f => f
    }
  }
}
