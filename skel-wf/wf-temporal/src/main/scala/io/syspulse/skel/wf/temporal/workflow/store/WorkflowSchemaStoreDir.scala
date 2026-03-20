package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.hacken.ext.wf.WorkflowSchema
import io.hacken.ext.wf.WorkflowSchemaJson._

class WorkflowSchemaStoreDir(dir:String = "store/") extends StoreDir[WorkflowSchema,Int](dir) with WorkflowSchemaStore {
  override val log = Logger(getClass)

  val store = new WorkflowSchemaStoreMem()

  // Convert String filename to Int ID
  def toKey(id:String):Int = id.toInt

  def all:Seq[WorkflowSchema] = store.all
  def size:Long = store.size
  override def +(w:WorkflowSchema):Try[WorkflowSchema] = super.+(w).flatMap(_ => store.+(w))
  override def del(id:Int):Try[Int] = super.del(id).flatMap(_ => store.del(id))
  override def ??(id:Int):Option[WorkflowSchema] = store.??(id)
  override def ???(id:Int): Try[WorkflowSchema] = store.???(id)

  // load directory
  load(dir)
}
