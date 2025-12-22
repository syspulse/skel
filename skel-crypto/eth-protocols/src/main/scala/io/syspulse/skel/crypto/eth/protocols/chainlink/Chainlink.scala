package io.syspulse.skel.crypto.eth.protocols.chainlink

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger
import scala.util.{Try,Success,Failure}

import io.syspulse.skel.blockchain.Blockchain
import io.syspulse.skel.crypto.eth.SolidityTuple
import io.syspulse.skel.crypto.eth.Web3jTrace
import io.syspulse.skel.crypto.Eth

import io.syspulse.skel.service.JsonCommon
import spray.json.RootJsonFormat
import io.syspulse.skel.blockchain.Token

case class ChainlinkContract(
  chain:String,
  name:String,
  addr:String,
  typ:Option[String] = None, 
  ver:Option[String] = Some("v0"),
  coin0:Option[String] = None,  
  info:Option[String] = None,
  dec:Option[Int]=Some(8),
  asset0:Option[String] = None, // base asset
  asset1:Option[String] = Some("USD"), // quote asset
  src:Option[Int] = Some(Chainlink.SRC_DEFAULT)
)

trait ChainlinkLoader {
  def load():Map[String,Set[ChainlinkContract]]
  def size(contracts:Map[String,Set[ChainlinkContract]]) = contracts.foldLeft(0)((a,c) => a + c._2.size)
}

class ChainlinkLoaderConfig(contractsConfig:Seq[String]) extends ChainlinkLoader {
  val log = Logger(s"${this}")
  
  def load():Map[String,Set[ChainlinkContract]] = {
    val contracts = contractsConfig
      .filter(_.nonEmpty)
      .filter(! _.trim.startsWith("#"))
      .flatMap(s => s.split("=").toList match {
        case _ if s.trim.startsWith("#") => 
          None
        
        case chain :: name :: addr :: typ :: coin0 :: dec :: ver :: Nil => 
          Some(ChainlinkContract(
            chain.toLowerCase.trim,
            name.trim,
            addr.toLowerCase.trim,
            Chainlink.toType(typ),
            ver=Some(ver.trim),
            coin0=Some(coin0.trim.toLowerCase),
            dec=Some(dec.trim.toInt),
            src = Some(Chainlink.SRC_CONFIG)))
        
        case chain :: name :: addr :: typ :: coin0 :: dec :: Nil => 
          Some(ChainlinkContract(
            chain.toLowerCase.trim,
            name.trim,
            addr.toLowerCase.trim,
            Chainlink.toType(typ),
            coin0=Some(coin0.trim.toLowerCase),
            dec=Some(dec.trim.toInt),
            src = Some(Chainlink.SRC_CONFIG)))

        case chain :: name :: addr :: typ :: coin0 :: Nil => 
          Some(ChainlinkContract(
            chain.toLowerCase.trim,
            name.trim,
            addr.toLowerCase.trim,
            Chainlink.toType(typ),
            coin0=Some(coin0.trim.toLowerCase),
            src = Some(Chainlink.SRC_CONFIG)))

        case _ => 
          log.warn(s"${s}: invalid contract config: '${s}'")
          None
      })
      .groupBy(_.chain)
      .map{ case (chain,pools) => chain -> pools.toSet }
    contracts
  }
}

class ChainlinkLoaderDefault() extends ChainlinkLoader {
  def load():Map[String,Set[ChainlinkContract]] = {
    Map[String,Set[ChainlinkContract]](
      Blockchain.ETHEREUM.name -> Set(
        ChainlinkContract(Blockchain.ETHEREUM.name,"ETH/USD","0x694AA1769357215DE4FAC081bf1f309aDC325306".toLowerCase,Some(Chainlink.ORACLE_TYPE_ID),info=Some("1%,86400s"),coin0=Some("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2".toLowerCase),asset0=Some("ETH"),asset1=Some("USD")),
      ),
      Blockchain.SCROLL_MAINNET.name -> Set(
        // ChainlinkContract(Blockchain.SCROLL_MAINNET.name,"Pool","0x11fCfe756c05AD438e312a7fd934381537D3cFfe".toLowerCase,Some(Aave.ORACLE_TYPE_ID))
      ),
    )
  }
}

// feed loader: https://reference-data-directory.vercel.app/feeds-mainnet.json
// format:
// {
//     "compareOffchain": "",
//     "contractAddress": "0x96d6e33B411dc1f4E3F1e894A5A5d9CE0F96738D",
//     "contractType": "",
//     "contractVersion": 6,
//     "decimalPlaces": null,
//     "ens": "link-usd",
//     "formatDecimalPlaces": null,
//     "healthPrice": "",
//     "heartbeat": 3600,
//     "history": null,
//     "multiply": "100000000",
//     "name": "LINK / USD",
//     "pair": [
//       "",
//       ""
//     ],
//     "path": "link-usd",
//     "proxyAddress": "0x2c1d072e956AFFC0D435Cb7AC38EF18d24d9127c",
//     "threshold": 0.5,
//     "valuePrefix": "",
//     "assetName": "Chainlink",
//     "feedCategory": "low",
//     "feedType": "Crypto",
//     "docs": {
//       "assetClass": "Crypto",
//       "baseAsset": "LINK",
//       "blockchainName": "Ethereum",
//       "clicProductName": "LINK/USD-RefPrice-DF-Ethereum-001",
//       "deliveryChannelCode": "DF",
//       "marketHours": "Crypto",
//       "productSubType": "Reference",
//       "productType": "Price",
//       "productTypeCode": "RefPrice",
//       "quoteAsset": "USD",
//       "quoteAssetClic": "USD_FX"
//     },
//     "decimals": 8
//   }
class ChainlinkLoaderFeed(feed:String = "file://feeds/feeds-mainnet.json",chain:String = Blockchain.ETHEREUM.name, warnings:Boolean = false) extends ChainlinkLoader {
  val log = Logger(s"${this}")

  case class Feed(
    contractAddress: String,
    name: String,
    assetName: Option[String],
    heartbeat: Option[Int],
    proxyAddress: Option[String],
    decimals: Int,
    contractType: Option[String] = None,
    contractVersion: Option[Int] = None,
    decimalPlaces: Option[Any] = None,
    ens: Option[String] = None,
    formatDecimalPlaces: Option[Any] = None,
    healthPrice: Option[String] = None,
    history: Option[Any] = None,
    multiply: Option[String] = None,
    pair: Option[Seq[String]] = None,
    path: Option[String] = None,
    threshold: Option[Double] = None,
    valuePrefix: Option[String] = None,
    valueSuffix: Option[String] = None,
    feedCategory: Option[String] = None,
    feedType: Option[String] = None,

    docs: Option[Map[String,Any]] = None
  )

  object FeedJson extends JsonCommon {
    implicit val feedFormat: RootJsonFormat[Feed] = jsonFormat22(Feed.apply _)
  }

  import FeedJson._
  import spray.json._

  def parseFeed(file:String,chain0:Option[String]=None):Map[String,Set[ChainlinkContract]] = {    
    val json = file.parseJson
    val feeds = json.convertTo[Seq[Feed]]
    
    val contracts = feeds.flatMap { feed =>
      
      val contractAddress = feed.contractAddress
      val name = feed.name
      val assetName = feed.assetName.getOrElse("")          
      val proxyAddress = feed.proxyAddress
      val decimals = feed.decimals
      val docs = feed.docs.getOrElse(Map())

      val addr = proxyAddress.getOrElse(contractAddress)
      if(addr.isEmpty) {
        log.warn(s"${name}: addresses undefined: ${contractAddress} / ${proxyAddress}")
        None
      } else {

        // find address of the coin0
        val (coin0,coin0Addr,typ) = docs.get("baseAsset").map(_.toString) match {
          case Some(coin0) if(docs.get("productTypeCode") == Some("RefPrice")) => 
            
            val a = Token
              .resolve(coin0.trim)
              .find(_.bid.toLowerCase == chain0.getOrElse(chain).toLowerCase)
              .map(_.addr)
            (Some(coin0),a,Some(Chainlink.ORACLE_TYPE_ID))

          case Some(coin0) if(docs.get("productTypeCode") == Some("PoR")) => 
            
            val a = Token
              .resolve(coin0)
              .find(_.bid.toLowerCase == chain0.getOrElse(chain).toLowerCase)
              .map(_.addr)
            (Some(coin0),a,Some(Chainlink.POR_TYPE_ID))

          case baseAsset =>
            val typ = docs.get("productTypeCode").map(_.toString)
            log.debug(s"${name}: not supported: '${baseAsset}' (${typ})")
            (None,None,typ)
        }

        if(coin0.isDefined && coin0Addr.isDefined) {

          val quoteAsset = docs.get("quoteAsset").map(_.toString)

          Some(ChainlinkContract(
            chain0.getOrElse(chain), 
            name, 
            addr,
            typ, 
            coin0 = coin0Addr,
            info = None,
            dec=Some(decimals),
            asset1=quoteAsset,
            src=Some(Chainlink.SRC_CHAINLINK)
          ))
        } else {
          if(coin0.isDefined && warnings)
            log.warn(s"${name}: address not found: '${coin0.getOrElse("")}'")
          None
        }
      }
    }

    contracts.groupBy(_.chain).map { case (chain, contracts) => 
      chain -> contracts.toSet 
    }    
  }

  def load():Map[String,Set[ChainlinkContract]] = {
    try {
      feed.split("://").toList match {
        case "file" :: path :: Nil =>
          val file = os.read(os.Path(path,os.pwd))
          parseFeed(file)
          
        case ("http" | "https") :: _ =>
          val file = requests.get(feed).text()
          parseFeed(file)

        case _ =>
          log.warn(s"${feed}: invalid feed protocol: '${feed}'")
          Map()
      }

    } catch {
      case e: Exception =>
        log.warn(s"${feed}: fail to load feed: '${feed}': ${e.getMessage}")
        Map()
    }
  }
}


// ATTENTION: Not thread-safe
class Chainlink(loaders:Seq[ChainlinkLoader],warnings:Boolean = false) {
  private val log = Logger(s"${this}")

  // chain -> contracts
  private var contracts: Map[String,Set[ChainlinkContract]] = Map()

  // addr -> contract
  private var tokens: Map[String,ChainlinkContract] = Map()
    
  // Instance methods
  def findOracle(chain:String,tokenAddr:String,quoteAsset:Option[String] = Some("USD")):Option[ChainlinkContract] = {
    tokens
      .get(tokenAddr.toLowerCase.trim)
      .filter(c => c.chain == chain)
      .filter(c => c.typ.exists(t => t == Chainlink.ORACLE_TYPE_ID || t == "oracle" ))
      .filter(c => quoteAsset.isEmpty || c.asset1 == quoteAsset)
  }

  def findPoR(chain:String,tokenAddr:String):Option[ChainlinkContract] = {
    tokens
      .get(tokenAddr.toLowerCase.trim)
      .filter(c => c.chain == chain)
      .filter(c => c.typ.exists(t => t == Chainlink.POR_TYPE_ID ))      
  }

  def findContract(chain:String,name:Option[String],typ:Option[String] = None):Option[ChainlinkContract] = {
    if(! name.isDefined || name.get.isEmpty)      
      return None

    contracts
      .get(chain)
      .flatMap(_.find(c => 
        c.name == name.get && 
        (typ.isEmpty || c.typ == typ)
      ))
  }
  
  def loadContracts(loaders:Seq[ChainlinkLoader]): Unit = {
    val cc = loaders.foreach(loader => {
      val newContracts = loader.load()
      log.info(s"Loader: ${loader}: ${loader.size(newContracts)} contracts (${newContracts.size} chains)")
      newContracts.foreach { case (chain, contractSet) =>
        val existingContracts = contracts.getOrElse(chain, Set.empty[ChainlinkContract])
        
        contracts += (chain -> (contractSet ++ existingContracts))
        
        tokens = tokens ++ contractSet
           .filter(c => c.coin0.isDefined)
           .filter(c => c.typ.exists(t => t == Chainlink.ORACLE_TYPE_ID || t == Chainlink.POR_TYPE_ID))
           .map(c => c.coin0.get -> c)
      }
    })
    val nOracles = contracts.values.flatten.filter(_.typ.contains(Chainlink.ORACLE_TYPE_ID)).size
    val nPor = contracts.values.flatten.filter(_.typ.contains(Chainlink.POR_TYPE_ID)).size
    val nChains = contracts.keys.mkString(",")
    log.info(s"Contracts: ${size()} (oracle=${nOracles},por=${nPor}), chains=[${nChains}], tokens=${tokens.size}")
  }

  def init() = {
    loadContracts(loaders)
  }

  def getPrice(chain:String,tokenAddr:String)(implicit web3:Web3jTrace):Try[Double] = {    
    if(contracts.size == 0) {      
      return Failure(new Exception("Protocol not initialized"))
    }

    val contract = findOracle(chain,tokenAddr)
    val funcName = "latestAnswer()(int256)"
    val params = Seq()

    if(! contract.isDefined) {
      // pool not found
      log.warn(s"${tokenAddr}: Oracle not found")
      return Failure(new Exception(s"${tokenAddr}: Oracle not found"))
    }

    log.info(s"${tokenAddr}: -> ${contract.get.addr}: ${funcName} ${params}")
    val r = Eth.callFunction(tokenAddr, contract.get.addr, funcName, params)(web3)
    log.info(s"${tokenAddr}: <- ${contract.get.addr}:  result=${r}")

    val price = r match {
      case Success(r) => 
        val dec = contract.get.dec.getOrElse(8)
        val price = r.toDouble / math.pow(10.0,dec.toDouble)
        Success(price)
      case Failure(e) => 
        log.warn(s"failed to get price: ${e.getMessage()}")
        Failure(new Exception(s"failed to get price: ${e.getMessage()}"))
    }

    price
  }
  
  def getPoR(chain:String,tokenAddr:String)(implicit web3:Web3jTrace):Try[Double] = {
    if(contracts.size == 0) {      
      return Failure(new Exception("Protocol not initialized"))
    }

    val contract = findPoR(chain,tokenAddr)
    val funcName = "latestAnswer()(int256)"
    val params = Seq()

    if(! contract.isDefined) {
      // pool not found
      log.warn(s"${tokenAddr}: Oracle not found")
      return Failure(new Exception(s"${tokenAddr}: Oracle not found"))
    }

    log.info(s"${tokenAddr}: -> ${contract.get.addr}: ${funcName} ${params}")
    val r = Eth.callFunction(tokenAddr, contract.get.addr, funcName, params)(web3)
    log.info(s"${tokenAddr}: <- ${contract.get.addr}:  result=${r}")

    val price = r match {
      case Success(r) => 
        val dec = contract.get.dec.getOrElse(8)
        val price = r.toDouble / math.pow(10.0,dec.toDouble)
        Success(price)
      case Failure(e) => 
        log.warn(s"failed to get price: ${e.getMessage()}")
        Failure(new Exception(s"failed to get price: ${e.getMessage()}"))
    }

    price
  }

  // Getter for contracts (if needed for external access)
  def getContracts(): Map[String,Set[ChainlinkContract]] = contracts

  def size() = contracts.foldLeft(0)((a,c) => a + c._2.size)
  
  // Method to check if initialized
  def isInitialized: Boolean = contracts.nonEmpty

  // initialize
  init()
}

object Chainlink {  
  
  val ORACLE_TYPE_ID = "feed"
  val STREAM_TYPE_ID = "stream"
  val POR_TYPE_ID = "por"

  val SRC_DEFAULT = 0
  val SRC_CONFIG = 1
  val SRC_CHAINLINK = 2  

  def toType(typ:String):Option[String] = {
    typ.toLowerCase.trim match {
      case "feed" => Some(ORACLE_TYPE_ID)
      case "oracle" => Some(ORACLE_TYPE_ID)
      case "stream" => Some(STREAM_TYPE_ID)
      case "por" => Some(POR_TYPE_ID)
    }
  }

  @volatile private var chainlink: Option[Chainlink] = None
  
  def apply(): Chainlink = {
    chainlink.getOrElse {
      chainlink.synchronized {
        chainlink.getOrElse {
          val instance = new Chainlink(Seq(
            new ChainlinkLoaderDefault(),
          ))
          chainlink = Some(instance)
          instance
        }
      }
    }
  }
  
  def apply(contractsConfig: Seq[String]): Chainlink = {
    chainlink.getOrElse {
      chainlink.synchronized {
        chainlink.getOrElse {
          val instance = new Chainlink(Seq(
            new ChainlinkLoaderDefault(),
            new ChainlinkLoaderFeed("https://reference-data-directory.vercel.app/feeds-mainnet.json"),
            new ChainlinkLoaderConfig(contractsConfig),
          ))          
          chainlink = Some(instance)
          instance
        }
      }
    }
  }
    
  // Method to reset singleton (useful for testing) - thread-safe
  def reset(): Unit = {
    chainlink.synchronized {
      chainlink = None
    }
  }
  
  // Check if singleton is initialized - thread-safe
  def isInitialized: Boolean = {
    chainlink.synchronized {
      chainlink.exists(_.isInitialized)
    }
  }
}
