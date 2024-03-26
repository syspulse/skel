package io.syspulse.skel.crypto.eth.abi3

import scala.jdk.CollectionConverters._
import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future,ExecutionContext}
import scala.jdk.FutureConverters._
import com.typesafe.scalalogging.Logger
import scala.jdk.CollectionConverters
import java.math.BigInteger

import io.syspulse.skel.util.Util

import org.web3j.utils.{Numeric}
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.abi.datatypes
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference

import spray.json._
import org.scalameta.data.data

case class AbiType(
  name:String,
  `type`:String
)

case class AbiDef(
  name:String,
  `type`:String,
  inputs:Option[Seq[AbiType]]=None,
  outputs:Option[Seq[AbiType]]=None,
  stateMutability:Option[String]=None,
  anonymous:Option[Boolean]=None,
  constant:Option[Boolean]=None,
  payable:Option[Boolean]=None
)

class Abi(definitions:Seq[AbiDef]) {

  override def toString = s"Abi(${definitions})"
  
  // build index by names
  private val functions = definitions.filter(a => a.`type` == "function").map(a => a.name -> a).toMap
  private val events = definitions.filter(a => a.`type` == "event").map(a => a.name -> a).toMap
  
  def parseType(typ:String,v:Any):Option[datatypes.Type[_]] = {    
    typ match {
      case "address" => Some(new datatypes.Address(v.toString))
      //case "function" => Some(new datatypes.Function(v.toString))

      case "bool" => Some(new datatypes.Bool(v.asInstanceOf[Boolean]))
      case "string" => Some(new datatypes.Utf8String(v.asInstanceOf[String]))
      case "bytes" => 
        val arr = v.asInstanceOf[Array[Byte]]
        Some(new datatypes.DynamicBytes(arr))

      case "uint" => Some(new datatypes.Uint(v.asInstanceOf[BigInteger]))
      case "uint8" => Some(new datatypes.generated.Uint8(v.asInstanceOf[BigInteger]))
      case "uint96" => Some(new datatypes.generated.Uint96(v.asInstanceOf[BigInteger]))
      case "uint160" => Some(new datatypes.generated.Uint160(v.asInstanceOf[BigInteger]))
      case "uint256" => Some(new datatypes.generated.Uint256(v.asInstanceOf[BigInteger]))

      case "int" => Some(new datatypes.Int(v.asInstanceOf[BigInteger]))
      case "int8" => Some(new datatypes.generated.Int8(v.asInstanceOf[BigInteger]))
      case "int96" => Some(new datatypes.generated.Int96(v.asInstanceOf[BigInteger]))
      case "int160" => Some(new datatypes.generated.Int160(v.asInstanceOf[BigInteger]))
      case "int256" => Some(new datatypes.generated.Int256(v.asInstanceOf[BigInteger]))

      // case t if(t.endsWith("[]")) =>
      //   val typ2 = t.stripSuffix("[]")
      //   val str = new datatypes.DynamicArray[] Utf8String(v.asInstanceOf[String])
      //   Some(new datatypes.DynamicBytes(arr))

      case _ => 
        throw new Exception(s"type '${typ}' not supported")
        None
    }
  }

  def parseTypeRef(typ:String):Option[TypeReference[_]] = {    
    typ match {
      case "address" => Some(new TypeReference[datatypes.Address]() {}) 
      case "bool" => Some(new TypeReference[datatypes.Bool]() {})
      case "string" => Some(new TypeReference[datatypes.Utf8String]() {}) 
      case "bytes" => Some(new TypeReference[datatypes.Bytes]() {}) 

      case "int" => Some(new TypeReference[datatypes.Int]() {})
      case "int256" => Some(new TypeReference[datatypes.generated.Int256]() {})
      case "int8" => Some(new TypeReference[datatypes.generated.Int8]() {})
      case "int96" => Some(new TypeReference[datatypes.generated.Int96]() {})      
      case "int160" => Some(new TypeReference[datatypes.generated.Int160]() {})      

      case "uint" => Some(new TypeReference[datatypes.Uint]() {})
      case "uint8" => Some(new TypeReference[datatypes.generated.Uint8]() {})
      case "uint96" => Some(new TypeReference[datatypes.generated.Uint96]() {})
      case "uint160" => Some(new TypeReference[datatypes.generated.Uint160]() {})
      case "uint256" => Some(new TypeReference[datatypes.generated.Uint256]() {})
      
      case _ => 
        throw new Exception(s"type '${typ}' not supported")
        None
    }
  }

  def getInputs(func:String,params:Seq[Any]):Seq[datatypes.Type[_]] = {
    functions.get(func.trim) match {      
      case Some(a) if(!a.inputs.isDefined) => Seq()
      case Some(a) => a.inputs.get.view.zipWithIndex.flatMap{ case(at,i) => parseType(at.`type`,params(i))}.toSeq
      case None => Seq()
    }
  }

  def getOutputs(func:String):Seq[TypeReference[_]] = {
    functions.get(func.trim) match {      
      case Some(a) if(!a.outputs.isDefined) => Seq()
      case Some(a) => a.outputs.get.view.zipWithIndex.flatMap{ case(at,i) => parseTypeRef(at.`type`)}.toSeq
      case None => Seq()
    }
  }
}

object Abi {
  import AbiJson._

  val log = Logger(s"${this}")

  def apply(abi:String) = {
     val definitions:Seq[AbiDef] = abi.parseJson.convertTo[Seq[AbiDef]]
     new Abi(definitions)
  }

  def parse(abiDef:String):Abi = {
    apply(abiDef)
  }

  def parseDef(abiDef:String):AbiDef = {
    val abi = abiDef.parseJson.convertTo[AbiDef]
    abi
  }
  
}