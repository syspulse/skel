package io.syspulse.skel.crypto.eth.abi

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.store.Store


trait SignatureStore[T <: AbiSignature] extends Store[T,String] {
  
  def getKey(s: T): String = s.getId()

  def +(s:T):Try[SignatureStore[T]]
  
  def del(id:String):Try[SignatureStore[T]]

  def ?(id:String):Try[T]  
  def all:Seq[T]
  def size:Long

  def findByTex(tex:String):Try[T]
  
}

