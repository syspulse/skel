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
  name:String, // e.g. contstructor
  `type`:String
)

case class AbiDef(
  name:Option[String], // e.g. contstructor
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

  def getDefinitions():Seq[AbiDef] = definitions
  
  // build index by names
  private val functions = definitions
    .filter(a => a.`type` == "function")
    .filter(a => a.name.isDefined)
    .map(a => a.name.get -> a)    
    .toMap
  
  private val events = definitions
    .filter(a => a.`type` == "event")
    .filter(a => a.name.isDefined)
    .map(a => a.name.get -> a)
    .toMap

  private val constructors = definitions
    .filter(a => a.`type` == "constructor")
    .map(a => "constructor" -> a)
    .toMap
  
  def getFunctions():Map[String,AbiDef] = functions
  def getEvents():Map[String,AbiDef] = events
  def getConstructors():Map[String,AbiDef] = constructors
  
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
      case "bytes1" => Some(new TypeReference[datatypes.generated.Bytes1]() {})
      case "bytes2" => Some(new TypeReference[datatypes.generated.Bytes2]() {})
      case "bytes4" => Some(new TypeReference[datatypes.generated.Bytes4]() {})
      case "bytes8" => Some(new TypeReference[datatypes.generated.Bytes8]() {})
      case "bytes16" => Some(new TypeReference[datatypes.generated.Bytes16]() {})
      case "bytes32" => Some(new TypeReference[datatypes.generated.Bytes32]() {})

      case "int" => Some(new TypeReference[datatypes.Int]() {})      
      case "int8" => Some(new TypeReference[datatypes.generated.Int8]() {})
      case "int96" => Some(new TypeReference[datatypes.generated.Int96]() {})      
      case "int160" => Some(new TypeReference[datatypes.generated.Int160]() {})      
      case "int256" => Some(new TypeReference[datatypes.generated.Int256]() {})

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

  def getInputs(func:String,params:Seq[Any]):Seq[datatypes.Type[_]] = findFunction(func).map(getInputs(_,params)).getOrElse(Seq())
  def getOutputs(func:String):Seq[TypeReference[_]] = findFunction(func).map(getOutputs(_)).getOrElse(Seq())

  def getInputs(a:AbiDef,params:Seq[Any]):Seq[datatypes.Type[_]] = {
    if(!a.inputs.isDefined) 
      Seq()
    else {
      a.inputs.get.view.zipWithIndex.flatMap{ case(at,i) => parseType(at.`type`,params(i))}.toSeq
    }
  }

  def getOutputs(a:AbiDef):Seq[TypeReference[_]] = {
    if(!a.outputs.isDefined) 
      Seq()
    else
      a.outputs.get.view.zipWithIndex.flatMap{ case(at,i) => parseTypeRef(at.`type`)}.toSeq
  }
    
  def getInputsOutputs(abi:AbiDef,params:Seq[Any]) = {
    (getInputs(abi,params),getOutputs(abi))
  }

  def findFunction(name0:String):Option[AbiDef] = {
    // remove any parenthesises
    val i = name0.indexOf("(")
    val name = if(i == -1)
      name0.trim
    else 
      name0.substring(0,i).trim
    functions.get(name)
  }

  def findEvent(name:String):Option[AbiDef] = {
    events.get(name.trim)
  }

  def getFunctionCall(name:String):Try[String] = {
    findFunction(name) match {
      case Some(a) => 
        val inputs = a.inputs.getOrElse(Seq()).map(i => i.`type`)
        val outputs = a.outputs.getOrElse(Seq()).map(i => i.`type`)
        val func = s"${a.name.get}(${inputs.mkString(",")})(${outputs.mkString(",")})"
        Success(func)
      case None => 
        Failure(new Exception(s"ABI function not found: '${name}'")) 
    }
  }

  def add(abi:Abi):Abi = {
    new Abi(definitions ++ abi.getDefinitions())
  }

  def ++(abi:Abi):Abi = this.add(abi)
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