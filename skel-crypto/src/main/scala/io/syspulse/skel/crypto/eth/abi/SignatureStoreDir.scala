package io.syspulse.skel.crypto.eth.abi

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import os._
import io.jvm.uuid._

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.crypto.eth.abi.AbiSignatureJson._

// Preload from file during start
abstract class SignatureStoreDir[T <: AbiSignature](dir:String = "store/")(implicit fmt:JsonFormat[T]) extends StoreDir[T,String](dir) with SignatureStore[T] {
  val store = new SignatureStoreMem[T]()

  def all:Seq[T] = store.all
  def size:Long = store.size
  override def +(u:T):Try[SignatureStoreDir[T]] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)

  override def del(id:String):Try[SignatureStoreDir[T]] = super.del(id).flatMap(_ => store.del(id)).map(_ => this)
  override def ?(id:String):Try[T] = store.?(id)

  override def findByTex(tex:String):Try[T] = store.findByTex(tex)
  
  // preload
  load(dir)
}