package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.Store
import io.hacken.ext.wf.WorkflowSchema

trait WorkflowSchemaStore extends Store[WorkflowSchema,Int] {
  private val log = Logger(getClass)

  def getKey(w: WorkflowSchema): Int = w.id
  def +(w:WorkflowSchema):Future[WorkflowSchema]

  def ??(id:Int):Option[WorkflowSchema]

  def ???(id:Int): Try[WorkflowSchema]

  def ?(id:Int):Future[WorkflowSchema] = {
    ??(id) match {
      case Some(data) => Future.successful(data)
      case None => Future.failed(new Exception(s"not found: '${id}'"))
    }
  }

  def del(id:Int): Future[Int]

  def all:Future[Seq[WorkflowSchema]]

  def size:Future[Long]
}
