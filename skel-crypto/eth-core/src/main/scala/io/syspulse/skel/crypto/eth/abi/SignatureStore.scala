package io.syspulse.skel.crypto.eth.abi

import scala.util.Try
import scala.concurrent.Future

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store
import scala.meta.tokens.Tokens


trait SignatureStore[T <: AbiSignature] extends Store[T,(String,Int)] {

  def getKey(s: T): (String,Int) = (s.getId(),s.getVer())

  def +(s:T):Future[T]

  def del(id:(String,Int)):Future[(String,Int)]

  def ?(id:(String,Int)):Future[T]

  def ??(id:String):Try[Vector[T]]

  def first(id:String):Try[T]

  def all:Future[Seq[T]]

  def all(from:Option[Int],size:Option[Int]):(Seq[T],Long)

  def size:Future[Long]

  def findByTex(tex:String):Try[T]

  def search(txt:String,from:Option[Int],size:Option[Int]):(Seq[T],Long)

}
