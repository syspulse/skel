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
  
  var enrolls: Map[UUID,Report] = Map()

  def all:Seq[Report] = enrolls.values.toSeq

  def size:Long = enrolls.size

  def +(enroll:Report):Try[ReportStore] = { 
    enrolls = enrolls + (enroll.id -> enroll)
    log.info(s"${enroll}")
    Success(this)
  }

  def del(id:UUID):Try[ReportStore] = { 
    val sz = enrolls.size
    enrolls = enrolls - id;
    log.info(s"${id}")
    if(sz == enrolls.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:UUID):Try[Report] = enrolls.get(id) match {
    case Some(p) => Success(p)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def findByXid(xid:String):Option[Report] = {
    enrolls.values.find(_.xid == xid)
  }
}
