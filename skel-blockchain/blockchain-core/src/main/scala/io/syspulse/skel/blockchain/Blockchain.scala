package io.syspulse.blockchain

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class Blockchain(
  name:String,
  id:Option[String] = None,  // chain_id 
  dec:Option[Int] = None,    // decimals
  tok:Option[String] = None
) {
  def asLong:Long = id.getOrElse("0").toLong
}

object Blockchain {
  type ID = String

  val ETHEREUM = Blockchain("ethereum",Some("1"),Some(18),Some("ETH"))
  val BSC_MAINNET = Blockchain("bsc",Some("56"),Some(8),Some("BNB"))
  val ARBITRUM_MAINNET = Blockchain("arbitrum",Some("42161"),Some(18),Some("ETH"))
  val OPTIMISM_MAINNET = Blockchain("optimism",Some("10"),Some(18),Some("ETH"))
  val POLYGON_MAINNET = Blockchain("polygon",Some("137"),Some(18),Some("MATIC"))
  val AVALANCHE_MAINNET = Blockchain("avalanche",Some("43114"),Some(18),Some("AVAX"))
  val FANTOM_MAINNET = Blockchain("fantom",Some("250"),Some(18),Some("FTM"))

  val SCROLL_MAINNET = Blockchain("scroll",Some("534352"),Some(18),Some("ETH"))
  val ZKSYNC_MAINNET = Blockchain("zksync",Some("324"),Some(18),Some("ETH"))
  val POLYGON_ZKEVM_MAINNET = Blockchain("polygon-zkevm",Some("1101"),Some(18),Some("ETH"))

  val LINEA_MAINNET = Blockchain("linea",Some("59144"),Some(18),Some("ETH"))
  val BASE_MAINNET = Blockchain("base",Some("8453"),Some(18),Some("ETH"))
  val BLAST_MAINNET = Blockchain("blast",Some("238"),Some(18),Some("ETH"))
  
  val SEPOLIA = Blockchain("sepolia",Some("11155111"),Some(18),Some("ETH"))
  val ANVIL = Blockchain("anvil",Some("31337"),Some(18),Some("ETH"))

  // default EVM
  val EVM = Blockchain("evm",Some("31337"),Some(18),Some("ETH"))

  val ALL = Map(
    ETHEREUM.id.get -> ETHEREUM,
    BSC_MAINNET.id.get -> BSC_MAINNET,
    ARBITRUM_MAINNET.id.get -> ARBITRUM_MAINNET,
    OPTIMISM_MAINNET.id.get -> OPTIMISM_MAINNET,
    POLYGON_MAINNET.id.get -> POLYGON_MAINNET,
    AVALANCHE_MAINNET.id.get -> AVALANCHE_MAINNET,
    FANTOM_MAINNET.id.get -> FANTOM_MAINNET,

    SCROLL_MAINNET.id.get -> SCROLL_MAINNET,
    ZKSYNC_MAINNET.id.get -> ZKSYNC_MAINNET,
    POLYGON_ZKEVM_MAINNET.id.get -> POLYGON_ZKEVM_MAINNET,

    LINEA_MAINNET.id.get -> LINEA_MAINNET,
    BASE_MAINNET.id.get -> BASE_MAINNET,
    BLAST_MAINNET.id.get -> BLAST_MAINNET,

    SEPOLIA.id.get -> SEPOLIA,
    ANVIL.id.get -> ANVIL,
    EVM.id.get -> EVM
  )

  def resolve(name:String):Option[Blockchain] = ALL.values.find(b => b.name.toLowerCase == name.trim.toLowerCase())
  
  def resolveById(id:String):Option[Blockchain] = {
    ALL.get(id)
  }
  
  def resolveChainId(chain:Blockchain):Option[Long] = chain.id match {
    case None => resolve(chain.name).map(b => b.id.get.toLong)
    case _ => Some(chain.id.get.toLong)
  }

  def resolve(chain:Blockchain):Try[Long] = Util.succeed(resolveChainId(chain)) match {
    case Success(chain) => Success(chain.get)
    case _ => Failure(new Exception(s"unknown chain: ${chain}"))
  }

  def resolve(chain:Option[Blockchain]):Try[Long] = chain match {
    case None => Failure(new Exception(s"unknown chain: ${chain}"))
    case Some(c) => resolve(c)
  }
  
  // disable temporarily
  // def apply(chain_id:String,network:String):Blockchain = new Blockchain(
  //   network.toLowerCase().trim match {
  //     case n => n
  //   },
  //   Some(chain_id)    
  // )

  // network (can be "ethereum", or "network_name:256" or "256" (will try to map to EVM))
  def apply(network:String):Blockchain = network.trim.toLowerCase.split("\\:").toList match {
    case "ethereum" :: Nil => ETHEREUM
    case "arbitrum"  :: Nil => ARBITRUM_MAINNET
    case "bsc"  :: Nil => BSC_MAINNET
    case "optimism" :: Nil => OPTIMISM_MAINNET
    case "polygon" :: Nil => POLYGON_MAINNET
    case "zksync" :: Nil => ZKSYNC_MAINNET
    case "scroll" :: Nil => ZKSYNC_MAINNET
    case "avalanche"  :: Nil => AVALANCHE_MAINNET

    case "linea" :: Nil => LINEA_MAINNET
    case "base" :: Nil => BASE_MAINNET
    case "blast" :: Nil => BLAST_MAINNET

    case "sepolia"  :: Nil => SEPOLIA
    case "anvil"  :: Nil => ANVIL

    case network :: id :: _ => Blockchain(network,Some(id))

    case id :: Nil => resolveById(id).getOrElse(new Blockchain("",Some(id)))

    case _ => Blockchain(network) 
  }
}