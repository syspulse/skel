package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

abstract class AbiStoreSignatures(funcStore:SignatureStore[FuncSignature],eventStore:SignatureStore[EventSignature]) 
  extends AbiStoreSigFuncResolver with AbiStoreSigEventResolver {

  def resolveFunc(sig:String) = funcStore.first(sig.toLowerCase()).map(_.tex).toOption
  def resolveEvent(sig:String) = eventStore.first(sig.toLowerCase()).map(_.tex).toOption
}

