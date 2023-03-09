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

class WorkflowStoreMem extends WorkflowStore {
  val log = Logger(s"${this}")
  
  var states: Map[Workflow.ID,Workflow] = Map()

  def all:Seq[Workflow] = states.values.toSeq

  def size:Long = states.size

  def +(wf:Workflow):Try[WorkflowStore] = { 
    states = states + (wf.id -> wf)
    log.info(s"add: ${wf}")
    Success(this)
  }

  def del(id:Workflow.ID):Try[WorkflowStore] = { 
    val sz = states.size
    states = states - id;
    log.info(s"del: ${id}")
    if(sz == states.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:Workflow.ID):Try[Workflow] = states.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }
 
  def update(id:Workflow.ID, data:Option[ExecData] = None, events:Option[Long] = None):Try[Workflow] = 
    this.?(id) match {
      case Success(wf) => 
        val wf1 = modify(wf,data,events)
        this.+(wf1)
        Success(wf1)
      case f => f
    }
}
