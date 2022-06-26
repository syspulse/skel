package io.syspulse.skel.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

trait Store[E,P]  {
  
  def +(e:E):Try[Store[E,P]]
  def -(e:E):Try[Store[E,P]]
  def del(id:P):Try[Store[E,P]]
  def get(id:P):Option[E]
  def getAll:Seq[E]
  def size:Long
}

