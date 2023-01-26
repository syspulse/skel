package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

case class AbiResult(name:String,params:Seq[(String,String,Any)])

trait AbiStore {
  protected val log = Logger(s"${this.getClass()}")

  def size:Long
  def find(contractAddr:String,functionName:String = "transfer"):Try[Seq[AbiDefinition]]
  def load():Try[AbiStore]
  def decodeInput(contract:String,data:String,selector:String):Try[AbiResult]
}

