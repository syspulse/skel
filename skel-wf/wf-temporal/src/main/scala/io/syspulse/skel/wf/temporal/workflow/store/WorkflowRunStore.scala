package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.Store
import io.hacken.ext.wf.WorkflowRun

trait WorkflowRunStore extends Store[WorkflowRun,String] {
  private val log = Logger(getClass)

  def getKey(w: WorkflowRun): String = w.rid.getOrElse(w.wid)
  def +(w:WorkflowRun):Try[WorkflowRun]

  def ??(id:String):Option[WorkflowRun]

  def ???(id:String): Try[WorkflowRun]

  def ?(id:String):Try[WorkflowRun] = {
    ??(id) match {
      case Some(data) => Success(data)
      case None => Failure(new Exception(s"not found: '${id}'"))
    }
  }

  def del(id:String): Try[String]

  def all:Seq[WorkflowRun]

  def size:Long
}
