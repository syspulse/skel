package io.syspulse.skel.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

// E - Entity
// P - Primary Key

trait Store[E,P] {
  //this: Store[E,P] =>

  def getKey(e:E):P

  def +(e:E):Try[Store[E,P]]
  def -(e:E):Try[Store[E,P]] = del(getKey(e))
  def del(id:P):Try[Store[E,P]]  
  def ?(id:P):Try[E]
  def ??(ids:Seq[P]):Seq[E] = ids.flatMap(id => ?(id).toOption)
  def all:Seq[E]
  def size:Long
}
