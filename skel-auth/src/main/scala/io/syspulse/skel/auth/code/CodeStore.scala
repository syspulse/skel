package io.syspulse.skel.auth.code

import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.store.Store

trait CodeStore extends Store[Code,String] {

  def getKey(code: Code): String = code.code
  def +(code:Code):Future[Code]
  def !(code:Code):Future[Code]
  //def -(code:Code):Future[CodeStore]
  def del(code:String):Future[String]
  def ?(code:String):Future[Code]
  def all:Future[Seq[Code]]
  def getByToken(token:String):Future[Option[Code]]
  def size:Future[Long]
}
