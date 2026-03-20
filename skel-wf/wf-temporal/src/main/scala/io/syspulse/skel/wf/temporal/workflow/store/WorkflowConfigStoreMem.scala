package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.hacken.ext.detector.DetectorConfig
import io.syspulse.skel.ErrNotFound

class WorkflowConfigStoreMem() extends WorkflowConfigStore {
  private val log = Logger(getClass)

  var configs:Map[Int,DetectorConfig] = Map()

  def +(w:DetectorConfig):Try[DetectorConfig] = {
    configs = configs + (getKey(w) -> w)
    Success(w)
  }

  def del(id:Int):Try[Int] = {
    configs = configs - id
    Success(id)
  }

  def ??(id:Int):Option[DetectorConfig] = configs.get(id)

  def ???(id:Int): Try[DetectorConfig] = {
    configs.get(id) match {
      case Some(data) => Success(data)
      case None =>
        Failure(new ErrNotFound(s"workflow config: ${id}"))
    }
  }

  def all:Seq[DetectorConfig] = configs.values.toSeq

  def size:Long = configs.size
}
