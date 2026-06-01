package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.Store
import io.hacken.ext.wf.WorkflowRun

trait WorkflowRunStore extends Store[WorkflowRun,String] {
  private val log = Logger(getClass)

  def getKey(w: WorkflowRun): String = w.rid.getOrElse(w.wid)
  def +(w:WorkflowRun):Future[WorkflowRun]

  def ??(id:String):Option[WorkflowRun]

  def ???(id:String): Try[WorkflowRun]

  def ?(id:String):Future[WorkflowRun] = {
    ??(id) match {
      case Some(data) => Future.successful(data)
      case None => Future.failed(new Exception(s"not found: '${id}'"))
    }
  }

  def del(id:String): Future[String]

  def all:Future[Seq[WorkflowRun]]

  def size:Future[Long]
}
