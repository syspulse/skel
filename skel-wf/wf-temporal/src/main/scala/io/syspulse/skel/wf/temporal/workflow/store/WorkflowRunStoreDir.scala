package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.hacken.ext.wf.WorkflowRun
import io.hacken.ext.wf.WorkflowRunJson._

class WorkflowRunStoreDir(dir:String = "store/") extends StoreDir[WorkflowRun,String](dir) with WorkflowRunStore {
  override val log = Logger(getClass)

  val store = new WorkflowRunStoreMem()

  // Convert String filename to String ID (rid)
  def toKey(id:String):String = id

  def all:Seq[WorkflowRun] = store.all
  def size:Long = store.size
  override def +(w:WorkflowRun):Try[WorkflowRun] = super.+(w).flatMap(_ => store.+(w))
  override def del(id:String):Try[String] = super.del(id).flatMap(_ => store.del(id))
  override def ??(id:String):Option[WorkflowRun] = store.??(id)
  override def ???(id:String): Try[WorkflowRun] = store.???(id)

  // load directory
  load(dir)
}
