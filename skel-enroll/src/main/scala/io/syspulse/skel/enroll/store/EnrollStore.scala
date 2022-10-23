package io.syspulse.skel.enroll.store

import scala.util.Try
import scala.util.Success

import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import scala.concurrent.Await

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.enroll.Enroll


trait EnrollStore extends Store[Enroll,UUID] {
  
  def +(e:Enroll):Try[EnrollStore] = {
    this.+(Option(e.xid))
    Success(this)
  }

  def +(xid:Option[String]):Try[UUID]
  def -(enroll:Enroll):Try[EnrollStore]
  def del(id:UUID):Try[EnrollStore]
  def ?(id:UUID):Option[Enroll] = {
    Await.result(???(id),FiniteDuration(1000L, TimeUnit.MILLISECONDS))
  }

  def ???(id:UUID):Future[Option[Enroll]]

  def all:Seq[Enroll]
  def size:Long

  def findByEmail(email:String):Option[Enroll]
}

