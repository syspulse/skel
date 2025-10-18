package io.syspulse.skel.blockchain.eth

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}
import scala.collection.mutable
import io.jvm.uuid.UUID

import io.syspulse.skel.util.Util

trait EvmEvent {
  val sig:String
  val name:String
}

class OwnershipTransferred(prevOwner:String,newOwner:String) extends EvmEvent {
  override val sig = EvmEvent.EVENT_OWNERSHIP_TRANSFERRED  
  override val name = "OwnershipTransferred"  
}

class Initialized(version:Int) extends EvmEvent {
  override val sig = EvmEvent.EVENT_INITIALIZED
  override val name = "Initialized"
}


trait EventDecoder[T] {
  val sig:String
  def decode(addr:String,data:String,topics:Array[String]):Try[T]
}

trait EventDecoders[T] {
  val decoders:Map[String,EventDecoder[T]]

  def decode(addr:String,data:String,topics:Array[String]):Try[T] = {
    if(topics.isEmpty) {
      return Failure(new Exception(s"${addr}: No topics provided"))
    }
    
    val sig = topics(0)
    decoders
      .get(sig)
      .map(d => d.decode(addr,data,topics))
      .getOrElse(Failure(new Exception(s"Unknown event signature: ${topics(0)}")))    
  }
}

object EvmEvent {
  val EVENT_OWNERSHIP_TRANSFERRED = "0x8be0079c531659141344cd1fd0a4f28419497f9722a3daafe3b4186f6b6457e0"
  val EVENT_INITIALIZED = "0xc7f505b2f371ae2175ee4913f4499e1f2633a7b5936321eed1cdaeb6115181d2" 
}