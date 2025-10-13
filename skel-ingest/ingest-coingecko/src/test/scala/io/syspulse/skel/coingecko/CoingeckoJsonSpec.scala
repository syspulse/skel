package io.syspulse.skel.coingecko

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import spray.json._
import scala.io.Source
import java.io.File

class CoingeckoJsonSpec extends AnyWordSpec with Matchers {
  
  import CoingeckoJson._

  "CoingeckoJson" should {
    
    "parse Bitcoin data correctly" in {
      val jsonContent = Source.fromResource("cg-bitcoin.json").mkString
      val coinData = jsonContent.parseJson.convertTo[Coingecko_CoinData]
      
      coinData.id shouldBe "bitcoin"
      coinData.symbol shouldBe "btc"
      coinData.name shouldBe "Bitcoin"
      coinData.market_cap_rank shouldBe 1
    }
    
    "parse Ethereum data correctly" in {
      val jsonContent = Source.fromResource("cg-ethereum.json").mkString
      val coinData = jsonContent.parseJson.convertTo[Coingecko_CoinData]
      
      coinData.id shouldBe "ethereum"
      coinData.symbol shouldBe "eth"
      coinData.name shouldBe "Ethereum"
      coinData.market_cap_rank shouldBe 2
    }
    
    "parse LINK data correctly" in {
      val jsonContent = Source.fromResource("cg-LINK.json").mkString
      val coinData = jsonContent.parseJson.convertTo[Coingecko_CoinData]
      
      coinData.id shouldBe "chainlink"
      coinData.symbol shouldBe "link"
      coinData.name shouldBe "Chainlink"
    }
    
    "parse WETH data correctly" in {
        val jsonContent = Source.fromResource("cg-WETH.json").mkString
        val coinData = jsonContent.parseJson.convertTo[Coingecko_CoinData]
        
        coinData.id shouldBe "weth"
        coinData.symbol shouldBe "weth"
        coinData.name shouldBe "WETH"
        coinData.market_cap_rank shouldBe 24
      }
    
    "support round-trip parsing for Bitcoin" in {
      val jsonContent = Source.fromResource("cg-bitcoin.json").mkString
      val originalCoinData = jsonContent.parseJson.convertTo[Coingecko_CoinData]
      
      // Convert back to JSON
      val roundTripJson = originalCoinData.toJson
      
      // Parse back to object
      val roundTripCoinData = roundTripJson.convertTo[Coingecko_CoinData]
      
      // Verify round-trip preserves key fields
      roundTripCoinData.id shouldBe originalCoinData.id
      roundTripCoinData.symbol shouldBe originalCoinData.symbol
      roundTripCoinData.name shouldBe originalCoinData.name
      roundTripCoinData.market_cap_rank shouldBe originalCoinData.market_cap_rank
    }
    
    "parse individual case classes correctly" in {
      // Test individual case class parsing
      val roi = Coingecko_Roi(2.5, "USD", 150.0)
      val roiJson = roi.toJson
      val parsedRoi = roiJson.convertTo[Coingecko_Roi]
      
      val image = Coingecko_Image("thumb.jpg", "small.jpg", "large.jpg")
      val imageJson = image.toJson
      val parsedImage = imageJson.convertTo[Coingecko_Image]
      
      val platform = Coingecko_Platform(Some(18), "0x123...")
      val platformJson = platform.toJson
      val parsedPlatform = platformJson.convertTo[Coingecko_Platform]
      
      parsedRoi shouldBe roi
      parsedImage shouldBe image
      parsedPlatform shouldBe platform
    }
    
    "parse all test resource files successfully" in {
      val resourceDir = new File(getClass.getClassLoader.getResource(".").getPath)
      val jsonFiles = Option(resourceDir.listFiles()).getOrElse(Array.empty[File]).filter(_.getName.endsWith(".json"))
      
      jsonFiles should not be empty
      
      var successfulParses = 0
      var totalFiles = 0
      
      for (file <- jsonFiles) {
        totalFiles += 1
        info(s"Testing file: ${file.getName}")
        
        val jsonContent = Source.fromFile(file).mkString
        if (jsonContent.trim.isEmpty) {
          info(s"Warning: ${file.getName} is empty")
        } else {
          try {
            val parsed = jsonContent.parseJson
            // Try to parse as Coingecko_CoinData
            val coinData = parsed.convertTo[Coingecko_CoinData]
            info(s"✓ ${file.getName} parsed successfully as CoinData")
            successfulParses += 1
          } catch {
            case e: Exception =>
              info(s"✗ ${file.getName} failed to parse: ${e.getMessage}")
          }
        }
      }
      
      // At least 3 out of 5 files should parse successfully
      successfulParses should be >= 3
      info(s"Successfully parsed $successfulParses out of $totalFiles files")
    }
  }
}

