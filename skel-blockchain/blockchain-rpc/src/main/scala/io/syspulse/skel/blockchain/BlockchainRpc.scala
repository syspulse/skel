package io.syspulse.blockchain

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.util.Util

import org.web3j.protocol.Web3j
import io.syspulse.skel.crypto.Eth

case class BlockchainRpc(name:String,id:String,rpcUri:String,explorer:Option[String]=None) 

class Blockchains(bb:Seq[String]) {

  override def toString():String = s"Blockchains(${rpc.toString})"

  protected var blockchains:Map[String,BlockchainRpc] = Map(
    // Blockchain.ETHEREUM.id.get -> BlockchainRpc(Blockchain.ETHEREUM.name,Blockchain.ETHEREUM.id.get,"https://eth.drpc.org",Blockchain.ETHEREUM.exp),
    
    
    Blockchain.ANVIL.id.get -> BlockchainRpc(Blockchain.ANVIL.name,Blockchain.ANVIL.id.get,"http://localhost:8545"),
    Blockchain.SEPOLIA.id.get -> BlockchainRpc(Blockchain.SEPOLIA.name,Blockchain.SEPOLIA.id.get,"https://rpc2.sepolia.org"),
  )

  def ++(bb:Seq[String]):Blockchains = {
    val newBlockchains = bb.flatMap(b =>{
      b.replaceAll("\n","").split("=").toList match {
        case name :: id :: rpc :: _ => 
          val bid = id.trim
          Some(( bid ->  BlockchainRpc(name.trim(),bid,rpc.trim()), bid -> Eth.web3(rpc.trim()) ))
        case rpc :: id :: Nil => 
          val bid = id.trim
          Some(( bid ->  BlockchainRpc(bid.toString,bid,rpc.trim()), bid -> Eth.web3(rpc.trim()) ))
        case rpc :: Nil => 
          if(rpc.isBlank())
            None
          else
            Some(( Blockchain.ETHEREUM.id.get ->  BlockchainRpc(Blockchain.ETHEREUM.name,Blockchain.ETHEREUM.id.get,rpc.trim()), Blockchain.ETHEREUM.id.get -> Eth.web3(rpc.trim()) ))
        case _ => None
      }
    })
    blockchains = blockchains ++ newBlockchains.map(_._1).toMap
    rpc = rpc ++ newBlockchains.map(_._2).toMap
    this
  }

  // map of connections
  var rpc:Map[String,Web3j] = blockchains.values.map( b => {
    b.id -> Eth.web3(b.rpcUri)
  }).toMap

  def get(id:Long) = blockchains.get(id.toString)
  def getByName(name:String) = blockchains.values.find(_.name == name.toLowerCase())
  def getWeb3(id:Long) = rpc.get(id.toString) match {
    case Some(web3) => Success(web3)
    case None => Failure(new Exception(s"RPC not found: ${id}"))
  }
  def getWeb3(name:String):Try[Web3j] = 
    Try(
      getByName(name)      
        .flatMap(b => rpc.get(b.id))
        .getOrElse(throw new Exception(s"RPC not found: '${name}'"))
    )

  def all():Seq[BlockchainRpc] = blockchains.values.toSeq

  // add default blockchains
  this.++(bb)
}

object Blockchains {
  def apply(bb:Seq[String]) = new Blockchains(bb)
  def apply(bb:String) = new Blockchains(bb.split(",").toSeq)
  def apply() = new Blockchains(Seq())
}