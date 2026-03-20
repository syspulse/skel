package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.Store
import io.hacken.ext.detector.DetectorConfig

trait WorkflowConfigStore extends Store[DetectorConfig,Int] {
  private val log = Logger(getClass)

  def getKey(w: DetectorConfig): Int = w.id
  def +(w:DetectorConfig):Try[DetectorConfig]

  def ??(id:Int):Option[DetectorConfig]

  def ???(id:Int): Try[DetectorConfig]

  def ?(id:Int):Try[DetectorConfig] = {
    ??(id) match {
      case Some(data) => Success(data)
      case None => Failure(new Exception(s"not found: '${id}'"))
    }
  }

  def del(id:Int): Try[Int]

  def all:Seq[DetectorConfig]

  def size:Long
}
