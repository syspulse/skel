package io.syspulse.skel.lake.job.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store

import io.syspulse.skel.lake.job._

object JobStore {
  type ID = String
}

import JobStore._

trait JobStore extends Store[Job,ID] {
  def getKey(n: Job): ID = n.xid
  
  def +(job:Job):Try[JobStore]
  
  def del(id:ID):Try[JobStore] = Success(this)
  def ?(id:ID):Try[Job] = Failure(new Exception(s"not supported"))
  def all:Seq[Job] = Seq()
  def size:Long = 0

}

