package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

trait AbiStoreSignaturesMem extends AbiStoreSigFuncResolver with AbiStoreSigEventResolver {

  var funcSignatures:Map[String,String] = Map(
    "0xa9059cbb" -> "transfer",
    "0x23b872dd" -> "transferFrom",
  )
  var eventSignatures:Map[String,String] = Map(
    "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef" -> "Transfer",
    "0x8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925" -> "Approval",
  )

  // must be override because it is a test mix-in
  override def resolveFunc(sig:String) = funcSignatures.get(sig.toLowerCase())
  override def resolveEvent(sig:String) = eventSignatures.get(sig.toLowerCase())
}

