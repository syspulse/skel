package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

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

  def +(s:AbiContract):Try[AbiStore]
  
  def del(id:AbiStore.ID):Try[AbiStore]

  def ?(id:AbiStore.ID):Try[AbiContract]

  def all:Seq[AbiContract]
  def size:Long

  
  def find(contractAddr:String,functionName:String):Try[Seq[AbiDefinition]]
  def load():Try[AbiStore]
  def decodeInput(contract:String,data:Seq[String],entity:String):Try[AbiResult]
}

object AbiStore {
  type ID = String

  val EVENT = "event"
  val FUNCTION = "function"
}