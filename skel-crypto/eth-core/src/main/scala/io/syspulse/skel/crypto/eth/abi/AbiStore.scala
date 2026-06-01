package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success
import scala.concurrent.Future

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

import io.syspulse.skel.store.Store

case class AbiContract(addr:String,json:String,ts0:Option[Long] = None)


trait AbiStoreSigFuncResolver {
  def resolveFunc(sig:String):Option[String]
}

trait AbiStoreSigEventResolver {
  def resolveEvent(sig:String):Option[String]
}

trait AbiStore extends Store[AbiContract,AbiStore.ID] with AbiStoreSigFuncResolver with AbiStoreSigEventResolver {
  def getKey(a: AbiContract):String = a.addr

  def events:SignatureStore[EventSignature]
  def functions:SignatureStore[FuncSignature]

  def +(s:AbiContract):Future[AbiContract]

  def del(id:AbiStore.ID):Future[AbiStore.ID]

  def ?(id:AbiStore.ID):Future[AbiContract]

  def search(txt:String,from:Option[Int],size:Option[Int]):(Seq[AbiContract],Long)

  def all:Future[Seq[AbiContract]]

  def all(from:Option[Int],size:Option[Int]):(Seq[AbiContract],Long)

  def size:Future[Long]

  def find(contractAddr:String,functionName:String):Try[Seq[AbiDefinition]]
  def load():String
  def decodeInput(contract:String,data:Seq[String],entity:String):Try[AbiResult]
}

object AbiStore {
  type ID = String

  val EVENT = "event"
  val FUNCTION = "function"
  val CONTRACT = "contract"
}