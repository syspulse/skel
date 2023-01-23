package io.syspulse.skel.auth.code

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CodeStore extends Store[Code,String] {
  
  def getKey(code: Code): String = code.code
  def +(code:Code):Try[CodeStore]
  def !(code:Code):Try[CodeStore]
  //def -(code:Code):Try[CodeStore]
  def del(code:String):Try[CodeStore]
  def ?(code:String):Try[Code]
  def all:Seq[Code]
  def getByToken(token:String):Option[Code]
  def size:Long
}

