package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.hacken.ext.detector.DetectorConfig
import io.hacken.ext.detector.DetectorConfigJson._

class WorkflowConfigStoreDir(dir:String = "store/") extends StoreDir[DetectorConfig,Int](dir) with WorkflowConfigStore {
  override val log = Logger(getClass)

  val store = new WorkflowConfigStoreMem()

  // Convert String filename to Int ID
  def toKey(id:String):Int = id.toInt

  def all:Seq[DetectorConfig] = store.all
  def size:Long = store.size
  override def +(w:DetectorConfig):Try[DetectorConfig] = super.+(w).flatMap(_ => store.+(w))
  override def del(id:Int):Try[Int] = super.del(id).flatMap(_ => store.del(id))
  override def ??(id:Int):Option[DetectorConfig] = store.??(id)
  override def ???(id:Int): Try[DetectorConfig] = store.???(id)

  // load directory
  load(dir)
}
