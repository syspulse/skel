package io.syspulse.skel.wf.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.wf._
import io.syspulse.skel.wf.runtime.ExecData

class WorkflowStoreMem extends WorkflowStore {
  val log = Logger(s"${this}")

  var states: Map[Workflow.ID,Workflow] = Map()

  def all:Future[Seq[Workflow]] = Future.successful(states.values.toSeq)

  def size:Future[Long] = Future.successful(states.size.toLong)

  def +(wf:Workflow):Future[Workflow] = {
    states = states + (wf.id -> wf)
    log.info(s"add: ${wf}")
    Future.successful(wf)
  }

  def del(id:Workflow.ID):Future[Workflow.ID] = {
    val sz = states.size
    states = states - id
    log.info(s"del: ${id}")
    if(sz == states.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:Workflow.ID):Future[Workflow] = states.get(id) match {
    case Some(u) => Future.successful(u)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  private def getSync(id:Workflow.ID):Try[Workflow] = states.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def update(id:Workflow.ID, data:Option[Map[String,Any]] = None):Try[Workflow] =
    getSync(id) match {
      case Success(wf) =>
        val wf1 = modify(wf,data)
        states = states + (wf1.id -> wf1)
        Success(wf1)
      case f => f
    }
}
