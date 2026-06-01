package io.syspulse.skel.notify.store

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.notify._
import io.syspulse.skel.store.Store

trait NotifyStore extends Store[Notify,UUID] {
  def getKey(n: Notify): UUID = n.id

  def notify(n:Notify):Future[Notify]

  // add during runtime on broadcast (with special processing for user.all,...)
  def ++(n:Notify):Future[Notify]

  def +(n:Notify):Future[Notify]

  def del(id:UUID):Future[UUID] = Future.failed(new Exception(s"not supported"))

  def all:Future[Seq[Notify]]
  def size:Future[Long]

  def ?(id:UUID):Future[Notify]
  // get by user id
  def ??(uid:UUID,fresh:Boolean):Future[Seq[Notify]]

  def ack(id:UUID):Future[Notify]
}
