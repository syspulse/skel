package io.syspulse.skel.wf.store

import scala.util.Try
import scala.concurrent.{Future, ExecutionContext}
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

class WorkflowStoreDir(dir:String = "store/workflows") extends StoreDir[Workflow,Workflow.ID](dir) with WorkflowStore {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global
  val store = new WorkflowStoreMem

  def toKey(id:String):Workflow.ID = id
  def all:Future[Seq[Workflow]] = store.all
  def size:Future[Long] = store.size
  override def +(u:Workflow):Future[Workflow] = super.+(u).flatMap(_ => store.+(u))
  override def del(id:Workflow.ID):Future[Workflow.ID] = super.del(id).flatMap(_ => store.del(id))
  override def ?(id:Workflow.ID):Future[Workflow] = store.?(id)

  def update(id:Workflow.ID, data:Option[Map[String,Any]] = None):Try[Workflow] =
    store.update(id, data).flatMap(u => writeFile(u))

  // create directory
  os.makeDir.all(os.Path(dir,os.pwd))

  // preload
  load(dir)
}
