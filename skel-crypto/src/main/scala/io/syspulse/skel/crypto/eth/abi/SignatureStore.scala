package io.syspulse.skel.crypto.eth.abi

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store


trait SignatureStore[T <: AbiSignature] extends Store[T,(String,Int)] {
  
  def getKey(s: T): (String,Int) = (s.getId(),s.getVer())

  def +(s:T):Try[SignatureStore[T]]
  
  def del(id:(String,Int)):Try[SignatureStore[T]]

  def ?(id:(String,Int)):Try[T]

  def ??(id:String):Try[Vector[T]]

  def first(id:String):Try[T]

  def all:Seq[T]
  def size:Long

  def findByTex(tex:String):Try[T]
  
}
