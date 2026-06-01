package io.syspulse.skel.pdf.report.store

import scala.concurrent.Future
import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.pdf.report.Report

trait ReportStore extends Store[Report,UUID] {
  def getKey(r: Report): UUID = r.id
  def +(enroll:Report):Future[Report]
  def del(id:UUID):Future[UUID]
  def ?(id:UUID):Future[Report]
  def all:Future[Seq[Report]]
  def size:Future[Long]

  def findByXid(xid:String):Option[Report]
}
