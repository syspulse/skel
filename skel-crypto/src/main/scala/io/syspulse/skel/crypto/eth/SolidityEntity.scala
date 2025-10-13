package io.syspulse.skel.crypto.eth

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.collection.mutable

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash
import net.osslabz.evm.abi.decoder.AbiDecoder

trait SolidityEntity {
  def sigHex:String
  def sig:String

  def name:String
  def types:String    
}

abstract class SolidityEntityNamed(sig:String) extends SolidityEntity {  
  val name:String = {
    val parenIndex = sig.indexOf('(')
    if (parenIndex == -1) sig else sig.substring(0, parenIndex)
  }

  val types:String = {
    val parenIndex = sig.indexOf('(')
    if (parenIndex == -1) "" else sig.substring(parenIndex + 1, sig.lastIndexOf(')'))
  }
}

// --- Event --------------------------------------------------------------------
class SolidityEvent(eventDef:String) extends SolidityEntityNamed(eventDef) {
  private val _sigHex = Util.hex(Hash.keccak256(eventDef.getBytes()))

  override def toString:String = s"SolidityEvent(${eventDef},${_sigHex})"

  def sigHex:String = _sigHex
  def sig:String = eventDef
}

// --- Error --------------------------------------------------------------------
class SolidityError(eventDef:String) extends SolidityEntityNamed(eventDef) {
  private val _sigHex = Util.hex(Hash.keccak256(eventDef.getBytes())).take(2 + 8)

  override def toString:String = s"SolidityError(${eventDef},${_sigHex})"

  def sigHex:String = _sigHex
  def sig:String = eventDef
}

object SolidityError {
  
  def decodeErrorData(errors:Seq[SolidityError],output:String):Try[String] = {
    val sigSz = 2 + 8
    val sigHex = output.take(sigSz)
    errors.find(e => e.sigHex == sigHex) match {
      case Some(e) => 
        val data = output.drop(sigSz)
        SolidityTuple.decodeData(e.types,data)
          .map(data => s"${e.name}(${data})")

      case None => Failure(new Exception(s"SolidityError not found: ${sigHex}"))
    }
  }
}

// --- Function --------------------------------------------------------------------
class SolidityFunc(funcDef:String,decoder:AbiDecoder,sigHex0:Option[String] = None) extends SolidityEntityNamed(funcDef) {
  private val _sigHex = sigHex0.getOrElse(Util.hex(Hash.keccak256(funcDef.getBytes())).take(2 + 8))

  override def toString:String = s"SolidityFunc(${funcDef},${_sigHex})"

  def sigHex:String = _sigHex
  def sig:String = funcDef

  def decode(data:String):Try[String] = {    
    Try {
      val decode = decoder.decodeFunctionCall(data)
      val params = decode.getParams().asScala      
                  
      val r = for (p <- params)
        yield SolidityTuple.valueToString(0,p.getName(),p.getValue(),p.getType())
      
      r.mkString(",")
    }
  }
}
