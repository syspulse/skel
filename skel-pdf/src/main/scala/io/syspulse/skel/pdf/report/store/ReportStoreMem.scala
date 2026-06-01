package io.syspulse.skel.pdf.report.store

import scala.concurrent.Future
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import io.syspulse.skel.pdf.report.Report

class ReportStoreMem extends ReportStore {
  val log = Logger(s"${this}")

  var reports: Map[UUID,Report] = Map()

  def all:Future[Seq[Report]] = Future.successful(reports.values.toSeq)

  def size:Future[Long] = Future.successful(reports.size.toLong)

  def +(r:Report):Future[Report] = {
    reports = reports + (r.id -> r)
    log.info(s"${r}")
    Future.successful(r)
  }

  def del(id:UUID):Future[UUID] = {
    val sz = reports.size
    reports = reports - id
    log.info(s"${id}")
    if(sz == reports.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:UUID):Future[Report] = reports.get(id) match {
    case Some(p) => Future.successful(p)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  def findByXid(xid:String):Option[Report] = {
    reports.values.find(_.xid == xid)
  }
}
