package io.syspulse.skel.pdf.report.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.pdf.report.Report

trait ReportStore extends Store[Report,UUID] {
  
  def +(enroll:Report):Try[ReportStore]
  def -(enroll:Report):Try[ReportStore]
  def del(id:UUID):Try[ReportStore]
  def ?(id:UUID):Try[Report]
  def all:Seq[Report]
  def size:Long

  def findByXid(xid:String):Option[Report]
}

