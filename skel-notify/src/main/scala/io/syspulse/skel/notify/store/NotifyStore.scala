package io.syspulse.skel.notify.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.notify._
import io.syspulse.skel.store.Store

trait NotifyStore extends Store[Notify,UUID] {
  def getKey(n: Notify): UUID = n.id
  
  def notify(n:Notify):Try[Notify]

  // add during runtime on broadcast (with special processing for user.all,...)
  def ++(n:Notify):Try[Notify]

  def +(n:Notify):Try[Notify]
  
  def del(id:UUID):Try[UUID] = Failure(new Exception(s"not supported"))  
  
  def all:Seq[Notify]
  def size:Long

  def ?(id:UUID):Try[Notify]
  // get by user id
  def ??(uid:UUID,fresh:Boolean):Seq[Notify]

  def ack(id:UUID):Try[Notify]
}

