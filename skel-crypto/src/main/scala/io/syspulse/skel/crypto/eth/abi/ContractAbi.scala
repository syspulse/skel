package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

class ContractAbi(addr:String,abi:Seq[AbiDefinition]) {
  override def toString = s"${getClass().getSimpleName()}(${addr},${abi.size})"

  def getAbi():Seq[AbiDefinition] = abi  
}

case class ContractERC20(addr:String,abi:Seq[AbiDefinition],funcTo:String, funcValue:String, tokenName:String="") 
  extends ContractAbi(addr,abi)
{
  def name:String = tokenName
  override def toString = s"${getClass().getSimpleName()}(${addr},${abi.size},${name},${funcTo},${funcValue})"
}
