package io.syspulse.skel.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

trait Store[E,P] {
  this: Store[E,P] =>

  def +(e:E):Try[Store[E,P]]
  def -(e:E):Try[Store[E,P]]
  def del(id:P):Try[Store[E,P]]
  def ?(id:P):Try[E]
  def all:Seq[E]
  def size:Long
}
