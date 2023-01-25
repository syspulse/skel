package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

trait AbiStore {
  protected val log = Logger(s"${this.getClass()}")

  def size:Long
  def find(contractAddr:String,functionName:String = "transfer",name:String=""):Try[ContractAbi]
  def load():Try[AbiStore]
  def decodeInput(contract:String,input:String,selector:String):Try[Seq[(String,String,Any)]]
}

