package io.syspulse.skel.blockchain.evm

import scala.util.{Try,Success,Failure}
import scala.concurrent.Future
import scala.collection.immutable
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.util.Util

import io.syspulse.skel.blockchain.Blockchain
import io.syspulse.skel.blockchain.Blockchain._
import io.syspulse.skel.blockchain.BlockchainRpc

import io.syspulse.skel.crypto.eth.Web3jTrace
import io.syspulse.skel.crypto.Eth

class EvmBlockchains(bb:Seq[String]) {

  override def toString():String = s"EvmBlockchains(${rpc.toString})"

  protected var blockchains:Map[String,BlockchainRpc] = Map(
    // Blockchain.ETHEREUM.id.get -> BlockchainRpc(Blockchain.ETHEREUM.name,Blockchain.ETHEREUM.id.get,"https://eth.drpc.org",Blockchain.ETHEREUM.exp),        
    Blockchain.ANVIL.id.get -> BlockchainRpc(Blockchain.ANVIL.name,Blockchain.ANVIL.id.get,"http://localhost:8545"),
    Blockchain.ETHEREUM_SEPOLIA.id.get -> BlockchainRpc(Blockchain.ETHEREUM_SEPOLIA.name,Blockchain.ETHEREUM_SEPOLIA.id.get,"https://rpc2.sepolia.org"),
  )

  def ++(bb:Seq[String]):EvmBlockchains = {
    val newBlockchains = BlockchainRpc.from(bb)
    blockchains = blockchains ++ newBlockchains
    rpc = rpc ++ newBlockchains.map{ case(id,b) => (id -> Eth.web3(b.rpcUri))}
    this
  }

  // map of connections
  var rpc:Map[String,Web3jTrace] = blockchains.values.map( b => {
    b.id -> Eth.web3(b.rpcUri)
  }).toMap

  def get(id:Long) = blockchains.get(id.toString)
  def getByName(name:String) = blockchains.values.find(_.name == name.toLowerCase())
  def getWeb3(id:Long) = rpc.get(id.toString) match {
    case Some(web3) => Success(web3)
    case None => Failure(new Exception(s"RPC not found: ${id}"))
  }
  def getWeb3(name:String):Try[Web3jTrace] = 
    Try(
      getByName(name)      
        .flatMap(b => rpc.get(b.id))
        .getOrElse(throw new Exception(s"RPC not found: '${name}'"))
    )

  def all():Seq[BlockchainRpc] = blockchains.values.toSeq

  // add default blockchains
  this.++(bb)
}

object EvmBlockchains {
  def apply(bb:Seq[String]) = new EvmBlockchains(bb)
  def apply(bb:String) = new EvmBlockchains(bb.split(",").toSeq)
  def apply() = new EvmBlockchains(Seq())  
}