package io.syspulse.skel.auth.code

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CodeStore extends Store[Code,String] {
  
  def +(auth:Code):Try[CodeStore]
  def !(auth:Code):Try[CodeStore]
  def -(auth:Code):Try[CodeStore]
  def del(auid:String):Try[CodeStore]
  def ?(auid:String):Option[Code]
  def all:Seq[Code]
  def getByToken(token:String):Option[Code]
  def size:Long
}

