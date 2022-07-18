package io.syspulse.skel.enroll.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import io.syspulse.skel.enroll.Enroll

trait EnrollStore extends Store[Enroll,UUID] {
  
  def +(enroll:Enroll):Try[EnrollStore]
  def -(enroll:Enroll):Try[EnrollStore]
  def del(id:UUID):Try[EnrollStore]
  def ?(id:UUID):Option[Enroll]
  def all:Seq[Enroll]
  def size:Long

  def findByXid(xid:String):Option[Enroll]
}

