package io.syspulse.skel.wf.store

import scala.util.Try
import scala.concurrent.Future

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.wf._
import io.syspulse.skel.store.Store
import io.syspulse.skel.wf.runtime.ExecData

trait WorkflowStore extends Store[Workflow,Workflow.ID] {

  def getKey(wf: Workflow): Workflow.ID = wf.id
  def +(wf:Workflow):Future[Workflow]

  def del(id:Workflow.ID):Future[Workflow.ID]
  def ?(id:Workflow.ID):Future[Workflow]
  def all:Future[Seq[Workflow]]
  def size:Future[Long]

  def update(id:Workflow.ID, data:Option[Map[String,Any]] = None):Try[Workflow]

  protected def modify(wf:Workflow, data:Option[Map[String,Any]] = None):Workflow = {
    (for {
      wf0 <- Some(wf)
      wf1 <- Some(if(data.isDefined) wf0.copy(data = data.get) else wf0)
      wf2 <- Some(wf1)
      wf3 <- Some(wf2)
    } yield wf3).get
  }
}
