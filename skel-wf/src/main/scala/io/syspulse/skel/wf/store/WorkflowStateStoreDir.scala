package io.syspulse.skel.wf.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime.ExecData

import io.syspulse.skel.wf.WorkflowJson._
import io.syspulse.skel.wf.runtime.Workflowing
import io.syspulse.skel.wf.runtime.Executing

// Preload from file during start
class WorkflowStateStoreDir(dir:String = "store/") extends StoreDir[WorkflowState,Workflowing.ID](dir) with WorkflowStateStore {
  val store = new WorkflowStateStoreMem

  def all:Seq[WorkflowState] = store.all
  def size:Long = store.size
  override def +(u:WorkflowState):Try[WorkflowStateStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)

  override def del(id:Workflowing.ID):Try[WorkflowStateStoreDir] = super.del(id).flatMap(_ => store.del(id)).map(_ => this)
  override def ?(id:Workflowing.ID):Try[WorkflowState] = store.?(id)

  override def update(id:Workflowing.ID, states:Option[Seq[State]] = None, events:Option[Long] = None):Try[WorkflowState] = 
    store.update(id, states, events).flatMap(u => writeFile(u))

  override def commit(id:Workflowing.ID,eid:Executing.ID,data:ExecData,status:Option[String]):Try[WorkflowState] = 
    store.commit(id, eid, data, status).flatMap(u => writeFile(u))

  // create directory
  os.makeDir.all(os.Path(dir,os.pwd))

  // preload
  load(dir)
}