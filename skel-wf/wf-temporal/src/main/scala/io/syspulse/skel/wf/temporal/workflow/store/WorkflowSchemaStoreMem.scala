package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.hacken.ext.wf.WorkflowSchema
import io.syspulse.skel.ErrNotFound

class WorkflowSchemaStoreMem() extends WorkflowSchemaStore {
  private val log = Logger(getClass)

  var workflows:Map[Int,WorkflowSchema] = Map()

  def +(w:WorkflowSchema):Future[WorkflowSchema] = {
    workflows = workflows + (getKey(w) -> w)
    Future.successful(w)
  }

  def del(id:Int):Future[Int] = {
    workflows = workflows - id
    Future.successful(id)
  }

  def ??(id:Int):Option[WorkflowSchema] = workflows.get(id)

  def ???(id:Int): Try[WorkflowSchema] = {
    workflows.get(id) match {
      case Some(data) => Success(data)
      case None =>
        Failure(new ErrNotFound(s"workflow: ${id}"))
    }
  }

  def all:Future[Seq[WorkflowSchema]] = Future.successful(workflows.values.toSeq)

  def size:Future[Long] = Future.successful(workflows.size.toLong)
}
