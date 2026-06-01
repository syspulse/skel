package io.syspulse.skel.wf.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime.ExecData
import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.Executing

class WorkflowStateStoreMem extends WorkflowStateStore {
  val log = Logger(s"${this}")

  var states: Map[Workflowing.ID,WorkflowState] = Map()

  def all:Future[Seq[WorkflowState]] = Future.successful(states.values.toSeq)

  def size:Future[Long] = Future.successful(states.size.toLong)

  def +(ws:WorkflowState):Future[WorkflowState] = {
    states = states + (ws.id -> ws)
    Future.successful(ws)
  }

  def del(id:Workflowing.ID):Future[Workflowing.ID] = {
    val sz = states.size
    states = states - id
    if(sz == states.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:Workflowing.ID):Future[WorkflowState] = states.get(id) match {
    case Some(u) => Future.successful(u)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  private def getSync(id:Workflowing.ID):Try[WorkflowState] = states.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def update(id:Workflowing.ID, status:Option[WorkflowState.Status]=None,states:Option[Seq[State]] = None, events:Option[Long] = None):Try[WorkflowState] =
    getSync(id) match {
      case Success(ws) =>
        val ws1 = modify(ws,status,states,events)
        this.states = this.states + (ws1.id -> ws1)
        Success(ws1)
      case f => f
    }

  def commit(id:Workflowing.ID,eid:Executing.ID,data:ExecData,status:Option[String]):Try[WorkflowState] = {
    getSync(id) match {
      case Success(ws) =>
        val ws1 = ws.copy(states = ws.states :+ State(System.currentTimeMillis(),eid,data,status), count = ws.count + 1)
        states = states + (ws1.id -> ws1)
        log.debug(s"commiting: ${id}:${eid}: ${data}/${status}")
        Success(ws1)
      case f => f
    }
  }
}
