package io.syspulse.skel.wf.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.wf._
import io.syspulse.skel.store.Store
import io.syspulse.skel.wf.runtime.ExecData

trait WorkflowStore extends Store[Workflow,Workflow.ID] {
  
  def getKey(wf: Workflow): Workflow.ID = wf.id
  def +(wf:Workflow):Try[WorkflowStore]
  
  def del(id:Workflow.ID):Try[WorkflowStore]
  def ?(id:Workflow.ID):Try[Workflow]  
  def all:Seq[Workflow]
  def size:Long

  def update(id:Workflow.ID, data:Option[Map[String,Any]] = None):Try[Workflow]

  protected def modify(wf:Workflow, data:Option[Map[String,Any]] = None):Workflow = {    
    (for {
      wf0 <- Some(wf)
      wf1 <- Some(if(data.isDefined) wf0.copy(data = data.get) else wf0)
      wf2 <- Some(wf1) //Some(if(events.isDefined) wf1.copy(events = wf1.events + events.get) else wf1)
      wf3 <- Some(wf2)
    } yield wf3).get    
  }
}

