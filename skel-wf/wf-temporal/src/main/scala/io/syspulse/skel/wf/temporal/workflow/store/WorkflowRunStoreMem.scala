package io.syspulse.skel.wf.temporal.workflow.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.hacken.ext.wf.WorkflowRun
import io.syspulse.skel.ErrNotFound

class WorkflowRunStoreMem() extends WorkflowRunStore {
  private val log = Logger(getClass)

  var runs:Map[String,WorkflowRun] = Map()

  def +(w:WorkflowRun):Try[WorkflowRun] = {
    val key = getKey(w)
    runs = runs + (key -> w)
    // Also index by wid if rid is present and different from wid
    if (w.rid.isDefined && w.rid.get != w.wid) {
      runs = runs + (w.wid -> w)
    }
    Success(w)
  }

  def del(id:String):Try[String] = {
    // Remove by id and also by wid if the run exists
    runs.get(id).foreach { run =>
      if (run.rid.isDefined && run.rid.get != run.wid) {
        runs = runs - run.wid
      }
    }
    runs = runs - id
    Success(id)
  }

  def ??(id:String):Option[WorkflowRun] = runs.get(id)

  def ???(id:String): Try[WorkflowRun] = {
    runs.get(id) match {
      case Some(data) => Success(data)
      case None =>
        Failure(new ErrNotFound(s"workflow run: ${id}"))
    }
  }

  def all:Seq[WorkflowRun] = runs.values.toSeq.distinct

  def size:Long = runs.values.toSeq.distinct.size
}
