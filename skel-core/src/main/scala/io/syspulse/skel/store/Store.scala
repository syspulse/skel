package io.syspulse.skel.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

trait Store[E]  {
  
  def +(e:E):Try[Store[E]]
  def -(e:E):Try[Store[E]]
  def -(id:UUID):Try[Store[E]]
  def get(id:UUID):Option[E]
  def getAll:Seq[E]
  def size:Long
}

