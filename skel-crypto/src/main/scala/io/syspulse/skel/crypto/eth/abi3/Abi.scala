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
import org.web3j.abi.datatypes._
import org.web3j.abi.FunctionEncoder
import org.web3j.abi.TypeReference

import spray.json._

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
      case "uint" => Some(new datatypes.Uint(v.asInstanceOf[BigInteger]))
      case "uint256" => Some(new datatypes.generated.Uint256(v.asInstanceOf[BigInteger]))
      case _ => 
        throw new Exception(s"type '${typ}' not supported")
        None
    }
  }

  def parseTypeRef(typ:String):Option[TypeReference[_]] = {    
    typ match {
      case "address" => Some(new TypeReference[Address]() {})
      case "uint" => Some(new TypeReference[datatypes.Uint]() {})
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