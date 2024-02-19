package io.syspulse.skel.pdf.report.store

import scala.util.Try
import scala.util.{Success,Failure}
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

  def all:Seq[Report] = reports.values.toSeq

  def size:Long = reports.size

  def +(r:Report):Try[Report] = { 
    reports = reports + (r.id -> r)
    log.info(s"${r}")
    Success(r)
  }

  def del(id:UUID):Try[UUID] = { 
    val sz = reports.size
    reports = reports - id;
    log.info(s"${id}")
    if(sz == reports.size) Failure(new Exception(s"not found: ${id}")) else Success(id)  
  }

  def ?(id:UUID):Try[Report] = reports.get(id) match {
    case Some(p) => Success(p)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def findByXid(xid:String):Option[Report] = {
    reports.values.find(_.xid == xid)
  }
}
