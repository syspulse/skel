package io.syspulse.skel.store

import scala.util.Try

import scala.collection.immutable

trait ExtFormat[E] {
  def decode(data:String):Try[Seq[E]]
  def encode(e:E):String
}
