package io.syspulse.skel.crypto.eth

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.collection.mutable

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash

trait SolidityEntity {
  def sigHex:String
  def sig:String
    
}

abstract class SolidityEntityNamed(sig:String) extends SolidityEntity {  
  val name:String = sig.substring(0, sig.indexOf('('))

  val types:String = {
    val parenIndex = sig.indexOf('(')
    if (parenIndex == -1) "" else sig.substring(parenIndex + 1, sig.lastIndexOf(')'))
  }
}

class SolidityEvent(eventDef:String) extends SolidityEntityNamed(eventDef) {
  private val _sigHex = Util.hex(Hash.keccak256(eventDef.getBytes()))

  override def toString:String = s"SolidityEvent(${eventDef},${_sigHex})"

  def sigHex:String = _sigHex
  def sig:String = eventDef
}

class SolidityError(eventDef:String) extends SolidityEntityNamed(eventDef) {
  private val _sigHex = Util.hex(Hash.keccak256(eventDef.getBytes())).take(2 + 8)

  override def toString:String = s"SolidityError(${eventDef},${_sigHex})"

  def sigHex:String = _sigHex
  def sig:String = eventDef
}

object SolidityError {
  
  def decodeErrorData(errors:Seq[SolidityError],output:String):Try[String] = {
    val sigHex = output.take(2 + 8)
    errors.find(e => e.sigHex == sigHex) match {
      case Some(e) => 
        SolidityTuple.decodeData(e.types,output)
          .map(data => s"${e.name},${data}")

      case None => Failure(new Exception(s"SolidityError not found: ${sigHex}"))
    }
  }
}