package io.syspulse.skel.lake.job.store

import scala.util.{Try,Success,Failure}
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

import io.syspulse.skel.lake.job._

import io.syspulse.skel.lake.job.Job.ID

trait JobStore extends Store[Job,ID] {
  def getKey(j: Job): ID = j.id
  
  def +(job:Job):Try[JobStore] = Failure(new Exception(s"not supported"))

  def +(name:String,script:String,conf:Seq[String],inputs:Seq[String]):Try[Job]
  
  def del(id:ID):Try[JobStore]
  def ?(id:ID):Try[Job]
  def all:Seq[Job]
  def size:Long

  def getEngine:JobEngine
}

