package io.syspulse.skel.pdf.report.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.pdf.report.Report

trait ReportStore extends Store[Report,UUID] {
  def getKey(r: Report): UUID = r.id
  def +(enroll:Report):Try[Report]
  def del(id:UUID):Try[UUID]
  def ?(id:UUID):Try[Report]
  def all:Seq[Report]
  def size:Long

  def findByXid(xid:String):Option[Report]
}

