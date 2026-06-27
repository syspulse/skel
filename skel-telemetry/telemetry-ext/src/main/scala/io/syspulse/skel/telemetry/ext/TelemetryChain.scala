package io.syspulse.skel.telemetry.ext

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.Ingestable
import io.syspulse.skel.blockchain.Blockchain
import com.typesafe.scalalogging.Logger

case class Chain(
  name:String,
  id:Option[String],
  var blocks:Long = 0,
  var tx:Long = 0 ,
  var last:Long = System.currentTimeMillis(),
  var lastBlock:Long = 0,
)

case class TelemetryChain(
  key:String,
  var chains:Array[Chain],
  
) extends Ingestable {
  //val log = Logger(this.getClass)

  def findOrAdd(chain:String):Chain = synchronized {
    chains.find(_.name == chain) match {
      case Some(c) => c
      case None => 
        val c = Chain(chain, None)
        chains :+= c
        c
    }
  }

  def addTx(chain:String, tx:Long, txLastBlock:Option[Long]):TelemetryChain = synchronized { 

    val c = findOrAdd(chain)
    c.tx += tx
    c.last = System.currentTimeMillis()

    // weird way to count blocks
    if(txLastBlock.isDefined) {
      if(c.lastBlock != 0 && c.lastBlock != txLastBlock.get) {
        val delta = txLastBlock.get - c.lastBlock
      c.blocks += delta
      }
      c.lastBlock = txLastBlock.get
    }

    this
  }

  override def toString:String = {
    s"Telemetry(${key}, chains=${chains.mkString(",")})"
  }
}

object TelemetryChain {
  def apply():TelemetryChain = TelemetryChain("", Array())
}
