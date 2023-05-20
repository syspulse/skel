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

// Preload from file during start
class WorkflowStoreDir(dir:String = "store/workflows") extends StoreDir[Workflow,Workflow.ID](dir) with WorkflowStore {
  val store = new WorkflowStoreMem

  def toKey(id:String):Workflow.ID = id
  def all:Seq[Workflow] = store.all
  def size:Long = store.size
  override def +(u:Workflow):Try[WorkflowStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)

  override def del(id:Workflow.ID):Try[WorkflowStoreDir] = super.del(id).flatMap(_ => store.del(id)).map(_ => this)
  override def ?(id:Workflow.ID):Try[Workflow] = store.?(id)

  def update(id:Workflow.ID, data:Option[Map[String,Any]] = None):Try[Workflow] = store.update(id, data).flatMap(u => writeFile(u))

  // create directory
  os.makeDir.all(os.Path(dir,os.pwd))

  // preload
  load(dir)
}