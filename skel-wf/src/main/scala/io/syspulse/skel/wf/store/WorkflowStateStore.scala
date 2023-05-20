package io.syspulse.skel.wf.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.wf._
import io.syspulse.skel.store.Store
import io.syspulse.skel.wf.runtime.ExecData
import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.Executing
import scala.util.Success

trait WorkflowStateStore extends Store[WorkflowState,Workflowing.ID] {
  
  def getKey(ws: WorkflowState): Workflowing.ID = ws.id
  def +(ws:WorkflowState):Try[WorkflowStateStore]
  
  def del(id:Workflowing.ID):Try[WorkflowStateStore]
  def ?(id:Workflowing.ID):Try[WorkflowState]  
  def all:Seq[WorkflowState]
  def size:Long

  def update(id:Workflowing.ID,status:Option[String]=None,states:Option[Seq[State]]=None,count:Option[Long] = None):Try[WorkflowState]

  protected def modify(ws:WorkflowState,status:Option[String]=None,states:Option[Seq[State]] = None, count:Option[Long] = None):WorkflowState = {    
    (for {
      ws0 <- Some(ws)
      ws1 <- Some(if(states.isDefined) ws0.copy(states = states.get) else ws0)
      ws2 <- Some(if(count.isDefined) ws1.copy(count = ws1.count + count.get) else ws1)
      ws3 <- Some(if(status.isDefined) ws2.copy(status = status.get) else ws2)      
    } yield ws3).get
  }

  def commit(id:Workflowing.ID,eid:Executing.ID,data:ExecData,status:Option[String]):Try[WorkflowState]
}

