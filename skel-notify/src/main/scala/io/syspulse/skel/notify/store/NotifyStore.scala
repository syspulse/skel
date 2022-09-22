package io.syspulse.skel.notify.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.notify._
import io.syspulse.skel.store.Store

trait NotifyStore extends Store[Notify,UUID] {
  
  def +(notify:Notify):Try[NotifyStore]
  def -(notify:Notify):Try[NotifyStore]
  def del(id:UUID):Try[NotifyStore]
  def ?(id:UUID):Option[Notify]
  def all:Seq[Notify]
  def size:Long

  def findByEid(eid:String):Option[Notify]
}

