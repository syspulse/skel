package io.syspulse.skel.blockchain

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.util.Util

case class BlockchainRpc(name:String,id:String,rpcUri:String,explorer:Option[String]=None) 

object BlockchainRpc {
  def apply(bb:Seq[String]) = from(bb)
  def apply(bb:String) = from(bb.split(",").toSeq)
  def apply() = from(Seq())

  def from(bb:Seq[String]):Map[String,BlockchainRpc] = {
    bb.flatMap(b => {
      // Split by comma (primary delimiter), then by newline (convenience)
      val entries = b.split(",").flatMap(_.split("\n")).map(_.trim).filter(_.nonEmpty)
      
      // Filter out comment lines (comments only at beginning of line)
      val configLines = entries.filter(line => 
        !line.startsWith("#") && !line.startsWith("//")
      )
      
      // Process each entry
      configLines.flatMap(line => {
        if(line.isEmpty) None
        else {
          line.split("=").toList match {
            case name :: id :: rpc :: _ => 
              val bid = id.trim
              Some(( bid ->  BlockchainRpc(name.trim(),bid,rpc.trim()) ))
            case rpc :: id :: Nil => 
              val bid = id.trim
              Some(( bid ->  BlockchainRpc(bid.toString,bid,rpc.trim())))
            case rpc :: Nil => 
              if(rpc.isBlank())
                None
              else
                Some(( Blockchain.ETHEREUM.id.get ->  BlockchainRpc(Blockchain.ETHEREUM.name,Blockchain.ETHEREUM.id.get,rpc.trim())))
            case _ => None
          }
        }
      })
    })
    .toMap
  }
}