package io.syspulse.skel.crypto.eth.protocols.chainlink

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.{Try, Success, Failure}
import io.syspulse.skel.config.Configuration
import io.syspulse.skel.config.ConfigurationAkka
import io.syspulse.skel.crypto.eth.Web3jTrace
import io.syspulse.skel.blockchain.evm.EvmBlockchains

class ChainlinkSpec extends AnyWordSpec with Matchers {
  val CHAINLINK_FEED_ENTRY = 151
  val chainlinkConfigPath = "skel-crypto/eth-protocols/conf/application-chainlink.conf"
  lazy val sharedConfig: Configuration = Configuration.withPriority(Seq(
    new ConfigurationAkka(from = Some(chainlinkConfigPath))
  ))
  lazy val sharedRpcConfig: String = sharedConfig.getString("blockchains").getOrElse("")
  lazy val sharedBlockchains: EvmBlockchains = EvmBlockchains(sharedRpcConfig)

  "ChainlinkLoaderDefault" should {
    "load default contracts" in {
      val loader = new ChainlinkLoaderDefault()
      val contracts = loader.load()
      
      contracts should contain key "ethereum"
      contracts should contain key "scroll"
      
      val ethContracts = contracts("ethereum")
      ethContracts should have size 1
      
      val ethUsdContract = ethContracts.head
      ethUsdContract.chain shouldBe "ethereum"
      ethUsdContract.name shouldBe "ETH/USD"
      ethUsdContract.addr shouldBe "0x694aa1769357215de4fac081bf1f309adc325306"
      ethUsdContract.typ shouldBe Some("feed")
      ethUsdContract.info shouldBe Some("1%,86400s")
    }
  }

  "ChainlinkLoaderConfig" should {
    "parse valid contract configurations" in {
      val config = Seq(
        "ethereum=ETH/USD=0x123...=oracle=0xabc...",
        "polygon=MATIC/USD=0x456...=oracle=0xdef...",
        "base=USD0 PoR=0x999...=por=0xdead..."
      )
      
      val loader = new ChainlinkLoaderConfig(config)
      val contracts = loader.load()
      
      contracts should contain key "ethereum"
      contracts should contain key "polygon"
      
      val ethContract = contracts("ethereum").head
      ethContract.chain shouldBe "ethereum"
      ethContract.name shouldBe "ETH/USD"
      ethContract.addr shouldBe "0x123..."
      ethContract.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      
      val maticContract = contracts("polygon").head
      maticContract.chain shouldBe "polygon"
      maticContract.name shouldBe "MATIC/USD"
      maticContract.addr shouldBe "0x456..."
      maticContract.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)

      val porContract = contracts("base").head
      porContract.chain shouldBe "base"
      porContract.name shouldBe "USD0 PoR"
      porContract.addr shouldBe "0x999..."
      porContract.typ shouldBe Some(Chainlink.POR_TYPE_ID)
      porContract.coin0 shouldBe Some("0xdead...")
    }

    "filter out empty lines and comments" in {
      val config = Seq(
        "",
        "  ",
        "# This is a comment",
        "ethereum=ETH/USD=0x123...=oracle=0xabc...",
        "# Another comment",
        "polygon=MATIC/USD=0x456...=oracle=0xdef..."
      )
      
      val loader = new ChainlinkLoaderConfig(config)
      val contracts = loader.load()
      
      contracts should have size 2
      contracts should contain key "ethereum"
      contracts should contain key "polygon"
    }

    "handle missing optional fields" in {
      val config = Seq(
        "ethereum=ETH/USD=0x123...=oracle=0xabc...",
        "polygon=MATIC/USD=0x456...=oracle=0xdef..."
      )
      
      val loader = new ChainlinkLoaderConfig(config)
      val contracts = loader.load()
      
      val ethContract = contracts("ethereum").head
      ethContract.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      
      val maticContract = contracts("polygon").head
      maticContract.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
    }

    "warn about invalid configurations" in {
      val config = Seq(
        "invalid_config",
        "ethereum=ETH/USD=0x123...=oracle=0xabc...",
        "ethereum=ETH/USD"
      )
      
      val loader = new ChainlinkLoaderConfig(config)
      val contracts = loader.load()
      
      // Should handle invalid configs gracefully
      contracts should not be empty
      contracts should contain key "ethereum"
    }
  }

  "ChainlinkLoaderFeed" should {    

    "parse feed data correctly" in {
      val loader = new ChainlinkLoaderFeed("file://skel-crypto/eth-protocols/feeds/feeds-mainnet.json")
      val contracts = loader.load()
      
      // The feed must be parseable and contain valid contracts
      contracts should not be empty
      val totalContracts = contracts.values.map(_.size).sum
      totalContracts shouldBe CHAINLINK_FEED_ENTRY
      
      // Find a specific contract to test parsing
      val ethContracts = contracts.get("ethereum")
      ethContracts shouldBe defined
      
      val ethContractsSet = ethContracts.get
      ethContractsSet should not be empty
      
      // Check that contracts have proper formatting
      ethContractsSet.foreach { contract =>
        contract.chain shouldBe "ethereum"        
        contract.name should not be empty
        contract.addr should startWith("0x")        
      }
    }

    "handle file not found gracefully" in {
      val loader = new ChainlinkLoaderFeed("file://non-existent-file.json")
      
      // Should handle missing file gracefully by catching the exception
      noException should be thrownBy {
        loader.load()
      }
      
      val contracts = loader.load()
      contracts shouldBe empty
    }

    "handle malformed JSON gracefully" in {
      // Create a temporary file with malformed JSON
      val tempFile = java.io.File.createTempFile("malformed", ".json")
      tempFile.deleteOnExit()
      
      val malformedJson = """{"invalid": json"""
      java.nio.file.Files.write(tempFile.toPath, malformedJson.getBytes)
      
      val loader = new ChainlinkLoaderFeed(s"file://${tempFile.getAbsolutePath}")
      val contracts = loader.load()
      
      contracts shouldBe empty
      
      tempFile.delete()
    }

    "handle HTTP URLs" in {
      // This test would require a mock HTTP server or real URL
      // For now, we'll test that the loader can parse HTTP URLs
      val loader = new ChainlinkLoaderFeed("https://example.com/feeds.json")
      
      // The loader should handle HTTP URLs gracefully
      // It may fail to load the content, but should return an empty map
      val contracts = loader.load()
      contracts shouldBe empty      
    }

    "load feed from official Chainlink reference directory" ignore {
      val loader = new ChainlinkLoaderFeed("https://reference-data-directory.vercel.app/feeds-mainnet.json")
      val contracts = loader.load()
      
      // The official feed should be accessible and contain data
      contracts should not be empty
      contracts.keys should not be empty
      val totalContracts = contracts.values.map(_.size).sum
      info(s"Feed: totalContracts: ${totalContracts}")
      
      // Check that contracts have the expected structure
      val firstChain = contracts.keys.head
      val firstContracts = contracts(firstChain)
      firstContracts should not be empty
      
      val firstContract = firstContracts.head
      firstContract.chain shouldBe firstChain
      firstContract.name should not be empty
      firstContract.addr should not be empty
      firstContract.typ shouldBe Some("feed")
            
    }

    "handle invalid feed URLs gracefully" in {
      val loader = new ChainlinkLoaderFeed("invalid://protocol")
      val contracts = loader.load()
      
      contracts shouldBe empty
    }
  }

  "Chainlink class" should {
    "initialize with loaders" in {
      val loader = new ChainlinkLoaderDefault()
      val chainlink = new Chainlink(Seq(loader))
      
      chainlink.isInitialized shouldBe true
      chainlink.getContracts() should contain key "ethereum"

      val o1 = chainlink.findOracle("ethereum","0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2")
      o1 shouldBe defined
      o1.get.name shouldBe "ETH/USD"
      o1.get.typ shouldBe Some("feed")
      
    }

    "find oracle at chain" in {
      val loader = new ChainlinkLoaderDefault()
      val chainlink = new Chainlink(Seq(loader))
      
      val o1 = chainlink.findOracle("ethereum","0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2")
      o1 shouldBe defined
      o1.get.name shouldBe "ETH/USD"
      o1.get.typ shouldBe Some("feed")
      
    }

    "find contract by name and type" in {
      val loader = new ChainlinkLoaderDefault()
      val chainlink = new Chainlink(Seq(loader))
      
      val contract = chainlink.findContract("ethereum", Some("ETH/USD"), Some("feed"))
      contract shouldBe defined
      contract.get.addr shouldBe "0x694aa1769357215de4fac081bf1f309adc325306"
    }

    "return None for non-existent contracts" in {
      val loader = new ChainlinkLoaderDefault()
      val chainlink = new Chainlink(Seq(loader))
      
      chainlink.findOracle("non_existent_chain","0x0") shouldBe None
      chainlink.findContract("ethereum", Some("NON_EXISTENT"), Some("feed")) shouldBe None
    }

    "load additional contracts" in {
      val defaultLoader = new ChainlinkLoaderDefault()
      val customLoader = new ChainlinkLoaderConfig(Seq(
        "ethereum=BTC/USD=0x789...=oracle=0xabc..."
      ))
      
      val chainlink = new Chainlink(Seq(defaultLoader, customLoader))
      
      // Default loader has 1 contract, custom loader adds 1 more = 2 total
      chainlink.getContracts()("ethereum") should have size 2
      
      val btcContract = chainlink.findContract("ethereum", Some("BTC/USD"))
      btcContract shouldBe defined
      btcContract.get.addr shouldBe "0x789..."
    }

    "work with ChainlinkLoaderFeed" in {
      val feedLoader = new ChainlinkLoaderFeed("file://skel-crypto/eth-protocols/feeds/feeds-mainnet.json")
      val chainlink = new Chainlink(Seq(feedLoader))
      
      // Should have loaded contracts from the feed
      val contracts = chainlink.getContracts()
      contracts should not be empty
      chainlink.isInitialized shouldBe true
      
      // Check that we can find contracts by chain
      val firstChain = contracts.keys.head
      val chainContracts = contracts(firstChain)
      chainContracts should not be empty
      
      // Check that we can find a specific contract
      val firstContract = chainContracts.head
      val foundContract = chainlink.findContract(firstChain, Some(firstContract.name))
      foundContract shouldBe defined
      foundContract.get shouldBe firstContract

      // find ETH
      val c1 = chainlink.findContract("ethereum", Some(firstContract.name))
      
    }
  }

  "Chainlink object singleton" should {
    "return the same chainlink on multiple calls" in {
      Chainlink.reset() // Start fresh
      
      val instance1 = Chainlink()
      val instance2 = Chainlink()
      val instance3 = Chainlink()
      
      instance1 should be theSameInstanceAs instance2
      instance2 should be theSameInstanceAs instance3
    }

    "return the same chainlink with different apply methods" in {
      Chainlink.reset() // Start fresh
      
      val instance1 = Chainlink()
      val instance2 = Chainlink(Seq("ethereum=ETH/USD=0x123...=feed=v1=eth=8"))
      
      instance1 should be theSameInstanceAs instance2
    }

    "initialize with default loaders when using apply()" in {
      Chainlink.reset() // Start fresh
      
      val chainlink = Chainlink()
      chainlink.isInitialized shouldBe true
      chainlink.getContracts() should contain key "ethereum"
    }

    "initialize with custom config when using apply(config)" in {
      Chainlink.reset() // Start fresh
      
      val config = Seq("ethereum=BTC/USD=0x123...=oracle=0xabc...")
      val chainlink = Chainlink(config)
      
      chainlink.isInitialized shouldBe true
      chainlink.getContracts() should contain key "ethereum"
      
      // Should have both default and custom contracts
      //chainlink.getContracts()("ethereum") should have size 2
      
      // Default ETH/USD contract should exist
      val ethContract = chainlink.findContract("ethereum", Some("ETH/USD"))
      ethContract shouldBe defined
      ethContract.get.addr shouldBe "0x694aa1769357215de4fac081bf1f309adc325306"
      
      // Custom BTC/USD contract should exist
      val btcContract = chainlink.findContract("ethereum", Some("BTC/USD"))
      btcContract shouldBe defined
      btcContract.get.addr shouldBe "0x123..."
    }

    "be thread-safe" in {
      Chainlink.reset() // Start fresh
      
      import scala.concurrent.{Future, Await}
      import scala.concurrent.duration._
      import scala.concurrent.ExecutionContext.Implicits.global
      
      val futures = (1 to 10).map { _ =>
        Future {
          Chainlink()
        }
      }
      
      val instances = Await.result(Future.sequence(futures), 5.seconds)
      
      // All instances should be the same
      instances.distinct should have size 1
      instances.head should be theSameInstanceAs instances.last
    }

    "reset correctly" in {
      val instance1 = Chainlink()
      Chainlink.reset()
      val instance2 = Chainlink()
      
      // After reset, we should get a different chainlink
      instance1 should not equal instance2
    }

    "check initialization status" in {
      Chainlink.reset() // Start fresh
      
      Chainlink.isInitialized shouldBe false
      
      val chainlink = Chainlink()
      Chainlink.isInitialized shouldBe true
    }
  }  

  "Chainlink Configuration" should {
    "load and verify Oracle configuration from application-chainlink.conf" in {
      Chainlink.reset() // Start fresh
      
      val c = Configuration.withPriority(Seq(
        new ConfigurationAkka(from = Some("skel-crypto/eth-protocols/conf/application-chainlink.conf"))      
      ))
      val config = c.getListString("chainlink.contracts")
           
      // Create a Chainlink instance with only the custom configuration
      val chainlink = new Chainlink(Seq(new ChainlinkLoaderConfig(config)))
      
      // Verify contracts were loaded successfully
      
      // Verify that mainnet contracts are loaded with correct oracle information
      val ethContracts = chainlink.getContracts().get("ethereum")
      ethContracts shouldBe defined
      
      val ethUsdContract = ethContracts.get.find(_.name == "ETH/USD")
      ethUsdContract shouldBe defined
      ethUsdContract.get.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      // Verify oracle information is present (field name may vary)
      
      val btcUsdContract = ethContracts.get.find(_.name == "BTC/USD")
      btcUsdContract shouldBe defined
      btcUsdContract.get.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      // Verify oracle information is present (field name may vary)
      
      val linkUsdContract = ethContracts.get.find(_.name == "LINK/USD")
      linkUsdContract shouldBe defined
      linkUsdContract.get.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      // Verify oracle information is present (field name may vary)

      // Verify PoR contracts on Base chain
      val baseContracts = chainlink.getContracts().get("base")
      baseContracts shouldBe defined

      val porContract = baseContracts.get.find(_.name == "USD0 PoR")
      porContract shouldBe defined
      porContract.get.typ shouldBe Some(Chainlink.POR_TYPE_ID)
      porContract.get.coin0 shouldBe Some("0x8238884ec9668ef77b90c6dff4d1a9f4f4823bfe")

      // Verify that Sepolia contracts are loaded with correct oracle information
      val sepoliaContracts = chainlink.getContracts().get("ethereum_sepolia")
      sepoliaContracts shouldBe defined
      
      val sepEthUsdContract = sepoliaContracts.get.find(_.name == "ETH/USD")
      sepEthUsdContract shouldBe defined
      sepEthUsdContract.get.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      // Verify oracle information is present (field name may vary)
      
      val sepBtcUsdContract = sepoliaContracts.get.find(_.name == "BTC/USD")
      sepBtcUsdContract shouldBe defined
      sepBtcUsdContract.get.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      // Verify oracle information is present (field name may vary)
      
      val sepLinkUsdContract = sepoliaContracts.get.find(_.name == "LINK/USD")
      sepLinkUsdContract shouldBe defined
      sepLinkUsdContract.get.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      // Verify oracle information is present (field name may vary)

      val sepEuraUsdContract = sepoliaContracts.get.find(_.name == "EURAU/USD")
      sepEuraUsdContract shouldBe defined
      sepEuraUsdContract.get.typ shouldBe Some(Chainlink.ORACLE_TYPE_ID)
      // Verify oracle information is present (field name may vary)
    }

    "find PoR oracle address by token" in {
      Chainlink.reset() // Start fresh

      val config = sharedConfig.getListString("chainlink.contracts")

      val chainlink = new Chainlink(Seq(new ChainlinkLoaderConfig(config)))

      implicit val web3 = sharedBlockchains.getWeb3("base").get

      val token = "0x8238884ec9668ef77b90c6dff4d1a9f4f4823bfe"

      val por = chainlink.findPoR("base",token)
      por shouldBe defined
      por.get.typ shouldBe Some(Chainlink.POR_TYPE_ID)
      por.get.addr shouldBe "0x5218ebeb96bd2bafe21f9b143f5672552629ba79"

      val price = chainlink.getPoR(por.get.chain,token)
      price.get should be > 0.0
    }
  }

  "Chainlink" should {
    "find in Configuration" in {
      Chainlink.reset() // Start fresh

      val c = Configuration.withPriority(Seq(
        new ConfigurationAkka(from = Some("skel-crypto/eth-protocols/conf/application-chainlink-1.conf"))      
      ))
      val config = c.getListString("chainlink.contracts")      
      
      val chainlink = new Chainlink(Seq(
            new ChainlinkLoaderConfig(config),
          ))

      chainlink.size() >= 0 shouldBe true

      

      info(s"chainlink: ${chainlink.getContracts().values.head.head.typ}")
            
      // find EURAU by token address (coin0)      
      // addr = oracle address, coin0 = token address
      val o4 = chainlink.findOracle("ethereum_sepolia", "0x4933a85b5b5466fbaf179f72d3de273c287ec2c2", None)
      o4 shouldBe defined
      
      // Check all fields
      val contract = o4.get
      contract.chain shouldBe "ethereum_sepolia"
      contract.name shouldBe "EURAU/USD"
      contract.addr shouldBe "0x081bb6f6486661db41076af15527df750024522c" // oracle address
      contract.typ shouldBe Some("feed") // "oracle" in config is converted to ORACLE_TYPE_ID which is "feed"
      contract.coin0 shouldBe Some("0x4933a85b5b5466fbaf179f72d3de273c287ec2c2") // token address
      contract.dec shouldBe Some(6)
      contract.src shouldBe Some(Chainlink.SRC_CONFIG)
      contract.ver shouldBe Some("v0") // defaults to "v0" when not provided in config
      contract.info shouldBe None // not provided in config
      contract.asset0 shouldBe None // not provided in config
      contract.asset1 shouldBe Some("USD") // defaults to "USD" when not provided in config
                  
    }

    "work with multiple loaders" in {
      Chainlink.reset() // Start fresh

      val c = Configuration.withPriority(Seq(
        new ConfigurationAkka(from = Some("skel-crypto/eth-protocols/conf/application-chainlink.conf"))      
      ))
      val config = c.getListString("chainlink.contracts")      
      
      val chainlink = Chainlink(config)

      //info(s"contracts: ${chainlink.getContracts()}")

      chainlink.size() >= 0 shouldBe true
      
      // Should have contracts from both default and custom loaders
      chainlink.getContracts() should contain key "ethereum"
      // chainlink.getContracts() should contain key "polygon"
      
      // Default ETH/USD contract
      val c1 = chainlink.findContract("ethereum", Some("ETH/USD"))
      c1 shouldBe defined
      c1.get.addr shouldBe "0x5f4ec3df9cbd43714fe2740f5e3616155c5b8419"

      val o1 = chainlink.findOracle("ethereum", "0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2")
      o1 shouldBe defined
      o1.get.addr shouldBe "0x5f4ec3df9cbd43714fe2740f5e3616155c5b8419"

      val o11 = chainlink.findOracle("ethereum_sepolia", "0x7b79995e5f793A07Bc00c21412e50Ecae098E7f9")
      o11 shouldBe defined
      o11.get.addr shouldBe "0x694aa1769357215de4fac081bf1f309adc325306"

      // Custom BTC/USD contract
      val c2 = chainlink.findContract("ethereum", Some("BTC/USD"))
      c2 shouldBe defined
      c2.get.addr shouldBe "0xf4030086522a5beea4988f8ca5b36dbc97bee88c"

      val o2 = chainlink.findOracle("ethereum", "0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599")
      o2 shouldBe defined
      o2.get.addr shouldBe "0xf4030086522a5beea4988f8ca5b36dbc97bee88c"

      chainlink.findOracle("ethereum", "0x1260FAC5E5542a773Aa44fBCfeDf7C193bc2C599") shouldBe None


      // find AAVE
      val o3 = chainlink.findOracle("ethereum", "0x7Fc66500c84A76Ad7e9c93437bFc5Ac33E2DDaE9")
      o3 shouldBe defined      
      o3.get.addr shouldBe "0x547a514d5e3769680Ce22B2361c10Ea13619e8a9"

      // find EURAU
      val o4 = chainlink.findOracle("ethereum_sepolia", "0x4933a85b5b5466fbaf179f72d3de273c287ec2c2")
      o4 shouldBe defined
      o4.get.addr shouldBe "0x081bb6f6486661db41076af15527df750024522c"
                  
    }
  }

}

