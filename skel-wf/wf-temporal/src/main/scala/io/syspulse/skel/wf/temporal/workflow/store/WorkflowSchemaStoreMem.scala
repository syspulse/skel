package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.hacken.ext.wf.WorkflowSchema
import io.syspulse.skel.ErrNotFound

class WorkflowSchemaStoreMem() extends WorkflowSchemaStore {
  private val log = Logger(getClass)

  var workflows:Map[Int,WorkflowSchema] = Map()

  def +(w:WorkflowSchema):Try[WorkflowSchema] = {
    workflows = workflows + (getKey(w) -> w)
    Success(w)
  }

  def del(id:Int):Try[Int] = {
    workflows = workflows - id
    Success(id)
  }

  def ??(id:Int):Option[WorkflowSchema] = workflows.get(id)

  def ???(id:Int): Try[WorkflowSchema] = {
    workflows.get(id) match {
      case Some(data) => Success(data)
      case None =>
        Failure(new ErrNotFound(s"workflow: ${id}"))
    }
  }

  def all:Seq[WorkflowSchema] = workflows.values.toSeq

  def size:Long = workflows.size
}
