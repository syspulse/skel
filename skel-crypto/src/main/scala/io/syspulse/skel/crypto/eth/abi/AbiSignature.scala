package io.syspulse.skel.crypto.eth.abi

import com.typesafe.scalalogging.Logger

import scala.util.Try
import scala.util.Success

import codegen.Decoder
import codegen.AbiDefinition
import os._
import scala.util.Failure

object ABI {
  val FUNC_HASH_SIZE = "0x12345678".size
  val EVENT_HASH_SIZE = "0x".size + 64
}

object AbiSignature {
  def getKey(id:String,ver:Option[Int] = None) = s"${id}.${ver.getOrElse("0").toString}"

  def toSig(abiDef:AbiDefinition) = {
    abiDef.`type` match {
      case AbiStore.EVENT =>
        val name = abiDef.name.getOrElse("")
        val params = abiDef.inputs.get.map(p => s"${p.`type`}").mkString(",")
        s"${name}($params)"
      case AbiStore.FUNCTION =>
        // https://docs.soliditylang.org/en/v0.8.17/abi-spec.html
        // The return type of a function is not part of this signature.
        val name = abiDef.name.getOrElse("")
        val params = abiDef.inputs.get.map(p => s"${p.`type`}").mkString(",")
        s"${name}($params)"
      case _ => ""
    }    
  }
}

// collisions are possible
abstract class AbiSignature(hex:String,tex:String,ver:Option[Int] = None) {
  def getId() = hex
  def getKey() = AbiSignature.getKey(hex,ver)
  def getTex() = tex
  def getVer() = ver.getOrElse(0)
}

case class EventSignature(hex:String,tex:String,ver:Option[Int]=None) extends AbiSignature(hex,tex,ver)
case class FuncSignature(hex:String,tex:String,ver:Option[Int]=None) extends AbiSignature(hex,tex,ver)
