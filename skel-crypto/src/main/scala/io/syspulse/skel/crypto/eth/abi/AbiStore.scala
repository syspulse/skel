package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure


trait AbiStoreSigFuncResolver {
  def resolveFunc(sig:String):Option[String]
}

trait AbiStoreSigEventResolver {
  def resolveEvent(sig:String):Option[String]
}

trait AbiStore extends AbiStoreSigFuncResolver with AbiStoreSigEventResolver {
  protected val log = Logger(s"${this.getClass()}")

  def size:Long
  def find(contractAddr:String,functionName:String = "transfer"):Try[Seq[AbiDefinition]]
  def load():Try[AbiStore]
  def decodeInput(contract:String,data:Seq[String],entity:String):Try[AbiResult]
}

