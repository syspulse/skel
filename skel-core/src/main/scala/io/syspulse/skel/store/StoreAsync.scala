package io.syspulse.skel.store

import scala.util.{Try,Success,Failure}

import scala.collection.immutable

import io.jvm.uuid._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

// E - Entity
// P - Primary Key

trait StoreAsync[E,P] {  

  def getKey(e:E):P

  def +(e:E):Future[StoreAsync[E,P]]
  def -(e:E):Future[StoreAsync[E,P]] = del(getKey(e))
  def del(id:P):Future[StoreAsync[E,P]]
  def ?(id:P):Future[E]
  def ??(ids:Seq[P])(implicit ec:ExecutionContext):Future[Seq[E]] = Future.sequence(ids.map(id => ?(id)))

  def all:Future[Seq[E]]
  def size:Future[Long]
}
