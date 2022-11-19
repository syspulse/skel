package io.syspulse.skel.notify.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.notify._
import io.syspulse.skel.store.Store

trait NotifyStore extends Store[Notify,UUID] {
  
  def +(notify:Notify):Try[NotifyStore]
  
  def -(notify:Notify):Try[NotifyStore] = Success(this)
  def del(id:UUID):Try[NotifyStore] = Success(this)
  def ?(id:UUID):Option[Notify] = None
  def all:Seq[Notify] = Seq()
  def size:Long = 0

}

