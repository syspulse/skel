package io.syspulse.skel.crypto.eth.abi

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class SignatureStoreMem[T <: AbiSignature] extends SignatureStore[T] {
  val log = Logger(s"${this}")
  
  var sigs: Map[String,T] = Map()

  def all:Seq[T] = sigs.values.toSeq

  def size:Long = sigs.size

  def +(sig:T):Try[SignatureStoreMem[T]] = { 
    sigs = sigs + (sig.getId() -> sig)    
    Success(this)
  }

  def del(id:String):Try[SignatureStoreMem[T]] = { 
    val sz = sigs.size
    sigs = sigs - id;
    if(sz == sigs.size) Failure(new Exception(s"not found: ${id}")) else Success(this)  
  }

  def ?(id:String):Try[T] = sigs.get(id) match {
    case Some(u) => Success(u)
    case None => Failure(new Exception(s"not found: ${id}"))
  }

  def findByTex(tex:String):Try[T] = {
    sigs.values.find(_.getTex().toLowerCase == tex.toLowerCase()) match {
      case Some(u) => Success(u)
      case None => Failure(new Exception(s"not found: ${tex}"))
    }
  }

}
