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
class FuncSignatureStoreDir(dir:String = "store/func") extends SignatureStoreDir[FuncSignature](dir) with AbiStoreSigFuncResolver {
  override def resolveFunc(sig: String): Option[String] = store.first(sig).toOption.map(_.tex)
}