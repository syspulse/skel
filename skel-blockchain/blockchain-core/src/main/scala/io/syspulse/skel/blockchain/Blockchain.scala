package io.syspulse.skel.blockchain

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import io.syspulse.skel.Ingestable
import io.syspulse.skel.util.Util

case class Blockchain(
  name:String,
  id:Option[String] = None,  // chain_id 
  dec:Option[Int] = None,    // decimals
  tok:Option[String] = None, // native token
  exp:Option[String] = None  // explorer url
) {
  def asLong:Long = id.getOrElse("0").toLong
}

// NOTE:  chainlist.org

object Blockchain {
  type ID = String

  val ETHEREUM = Blockchain("ethereum",Some("1"),Some(18),Some("ETH"),Some("https://etherscan.io"))
  val BSC_MAINNET = Blockchain("bsc",Some("56"),Some(18),Some("BNB"),Some("https://bscscan.com"))
  val ARBITRUM_MAINNET = Blockchain("arbitrum",Some("42161"),Some(18),Some("ETH"),Some("https://arbiscan.io"))
  val OPTIMISM_MAINNET = Blockchain("optimism",Some("10"),Some(18),Some("ETH"),Some("https://optimistic.etherscan.io"))
  val POLYGON_MAINNET = Blockchain("polygon",Some("137"),Some(18),Some("POL"),Some("https://polygonscan.com"))
  val AVALANCHE_MAINNET = Blockchain("avalanche",Some("43114"),Some(18),Some("AVAX"),Some("https://snowtrace.io"))
  val FANTOM_MAINNET = Blockchain("fantom",Some("250"),Some(18),Some("FTM"),Some("https://ftmscan.com"))

  val SCROLL_MAINNET = Blockchain("scroll",Some("534352"),Some(18),Some("ETH"),Some("https://scrollscan.com"))
  val ZKSYNC_MAINNET = Blockchain("zksync",Some("324"),Some(18),Some("ETH"),Some("https://explorer.zksync.io"))
  val POLYGON_ZKEVM_MAINNET = Blockchain("polygon_zkevm",Some("1101"),Some(18),Some("ETH"),Some("https://polygonzkevm.io"))

  val LINEA_MAINNET = Blockchain("linea",Some("59144"),Some(18),Some("ETH"),Some("https://lineaexplorer.io"))
  val BASE_MAINNET = Blockchain("base",Some("8453"),Some(18),Some("ETH"),Some("https://basescan.org"))
  val BLAST_MAINNET = Blockchain("blast",Some("238"),Some(18),Some("ETH"),Some("https://blastscan.io"))

  val TELOS_MAINNET = Blockchain("telos",Some("40"),Some(18),Some("TLOS"),Some("https://teloscan.io"))
  val TRON_MAINNET = Blockchain("tron",Some("728126428"),Some(18),Some("TRX"),Some("https://tronscan.org"))

  val ZETA_MAINNET = Blockchain("zeta",Some("7000"),Some(18),Some("ZETA"),Some("https://explorer.zetachain.com"))
  
  // test networks
  val BSC_TESTNET = Blockchain("bsc_testnet",Some("97"),Some(18),Some("BNB"),Some("https://testnet.bscscan.com"))    
  val ETHEREUM_SEPOLIA = Blockchain("ethereum_sepolia",Some("11155111"),Some(18),Some("ETH"),Some("https://sepolia.etherscan.io"))  
  val ANVIL = Blockchain("anvil",Some("31337"),Some(18),Some("ETH"),Some("https://otterscan.dev.hacken.cloud"))  
  val ETHEREUM_HOLESKY = Blockchain("ethereum_holesky",Some("17000"),Some(18),Some("ETH"),Some("https://holesky.etherscan.io"))
  val POLYGON_AMOY = Blockchain("polygon_amoy",Some("80002"),Some(18),Some("POL"),Some("https://amoy.polygonscan.com"))

  // default EVM
  val EVM = Blockchain("evm",Some("0"),Some(18),Some("ETH"))

  // ------------------------------------------------------------------------------------
  val BITCOIN = Blockchain("bitcoin",None,Some(8),Some("BTC"),Some("https://blockstream.info"))

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

    // 
    TELOS_MAINNET.id.get -> TELOS_MAINNET,
    TRON_MAINNET.id.get -> TRON_MAINNET,

    ZETA_MAINNET.id.get -> ZETA_MAINNET,

    // test networks
    BSC_TESTNET.id.get -> BSC_TESTNET,
    ETHEREUM_SEPOLIA.id.get -> ETHEREUM_SEPOLIA,
    ANVIL.id.get -> ANVIL,    

    ETHEREUM_HOLESKY.id.get -> ETHEREUM_HOLESKY,
    POLYGON_AMOY.id.get -> POLYGON_AMOY,

    EVM.id.get -> EVM
  )

  val ALL_NAMES = ALL.values.map(b => b.name.toLowerCase -> b).toMap
  
  def resolveById(id:String):Option[Blockchain] = {
    ALL.get(id)
  }

  def resolveByName(name:String):Option[Blockchain] = {
    //ALL.values.find(b => b.name.toLowerCase == name.trim.toLowerCase())
    ALL_NAMES.get(name.trim.toLowerCase())
  }
  
  def resolveChainId(chain:Blockchain):Option[Long] = chain.id match {
    case Some("") | None => resolveByName(chain.name).map(b => b.id.get.toLong)    
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
  
  def resolve(network:String):Option[Blockchain] = {
    network.trim.toLowerCase.split("\\:").toList match {
      case ("sepolia" | "ethereum-sepolia" | "ethereum_sepolia")  :: Nil => Some(ETHEREUM_SEPOLIA)
      case "anvil"  :: Nil => Some(ANVIL)
      case ("bsc-testnet" | "bsc_testnet")  :: Nil => Some(BSC_TESTNET)
      case ("holesky" | "ethereum_holesky")  :: Nil => Some(ETHEREUM_HOLESKY)
      case ("amoy" | "polygon_amoy")  :: Nil => Some(POLYGON_AMOY)
      
      case network :: id :: _ => Some(new Blockchain(network,Some(id)))

      case "" :: Nil => Some(new Blockchain("",None))

      case id :: Nil => 
        if(id(0).isDigit)
          resolveById(id).orElse(Some(new Blockchain("",Some(id))))
        else
          resolveByName(id).orElse(Some(new Blockchain(id)))

      case _ => None
    }
  }

  // network (can be "ethereum", or "network_name:256" or "256" (will try to map to EVM))
  def apply(network:String):Blockchain = 
    resolve(network) match {
      case Some(b) => b
      case _ => new Blockchain(network)
    }

  def apply(network:Option[String]):Option[Blockchain] = 
    network.map(apply)
  

  def getExplorerTx(chain:Option[String],txHash:String):String = {
    chain match {
      case Some(c) => Blockchain.resolveByName(c).flatMap(_.exp).map(e => s"${e}/tx/${txHash}").getOrElse(txHash)
      case None => txHash
    }
  }

  def getExplorerBlock(chain:Option[String],block:String):String = {
    chain match {
      case Some(c) => Blockchain.resolveByName(c).flatMap(_.exp).map(e => s"${e}/block/${block}").getOrElse(block)
      case None => block
    }
  }

}