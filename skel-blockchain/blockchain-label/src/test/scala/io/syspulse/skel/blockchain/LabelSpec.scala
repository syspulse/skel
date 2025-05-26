package io.syspulse.skel.blockchain

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._

import io.syspulse.skel.util.Util
import io.syspulse.skel.test.TestUtil

class LabelsSpec extends AnyWordSpec with Matchers with TestUtil {
  
  "LabelsSpec" should {
    
    "load labels + override with custom" in {      
      val c1 = new LabelStoreMem("exploit",
        Set("tag1","tag2"),
        Seq("0x22ff777ef6fe0690f1f74c6758126909653ad56a","0x9d6971bf21b0e94724a7134371a8f7f41bb9db88",
            "0xf1dA173228fcf015F43f3eA15aBBB51f0d8f1123"),
        Seq(new EtherscanLabelLoader(s"${projectDir}/skel-blockchain/blockchain-label/store_labels/etherscan/combinedAllLabels.json")))
      
      c1.size shouldBe 29773

      // must override original label
      c1.?("0x22FF777ef6fe0690f1f74c6758126909653ad56A") shouldBe a[Success[_]]
      c1.?("0x22FF777ef6fe0690f1f74c6758126909653ad56A").get.tags shouldBe Set("tag1","tag2")

      c1.?("0xf1da173228fcf015f43f3ea15aBBB51f0d8f1123").get.tags shouldBe Set("tag1","tag2")
    }

    "load exclude addresses" in {
      val exc1 = """
       0xf1dA173228fcf015F43f3eA15aBBB51f0d8f1123, eXch
            0xDFaa75323fB721e5f29D43859390f62Cc4B600b8, shuffle.com
            """
      
      val c1 = new LabelStoreMem("exploit",
        Set(),
        Seq(),
        Seq(new ListLoader(exc1)))
      
      c1.size shouldBe 2

      c1.?("0xF1da173228fcf015f43f3ea15aBBB51f0d8f1123") shouldBe a[Success[_]]
      
      // must override original label      
      val l2 = c1.?("0xf1DA173228fcf015f43f3ea15aBBB51f0d8f1123").get
      l2.name shouldBe Some("eXch")
      l2.tags shouldBe Set()
    }        
  }
}

class EtherscanLabelLoaderSpec extends AnyWordSpec with Matchers with TestUtil {
  
  "EtherscanLabelLoader" should {
    
    "load labels from file" in {
      val loader = new EtherscanLabelLoader()
      val testFile = "/tmp/etherscan-labels-test.json"
      os.remove(os.Path(testFile,os.pwd))
      
      // Create test file with sample data
      os.write(
        os.Path(testFile,os.pwd), 
        """{
          "0xABC123":{"labels":["suspicious","exploit"],"name":"Test1"},
          "0xfff456":{"labels":["scam"],"name":"Test2"}
        }""".stripMargin
      )

      val result = loader.load(testFile)
      result shouldBe a[Success[_]]
      result.get.size shouldBe 2      
      
      // Cleanup
      os.remove(os.Path(testFile,os.pwd))
    }

    "load Etherscan labels large file" in {
      val loader = new EtherscanLabelLoader()
            
      val result = loader.load(s"${projectDir}/skel-blockchain/blockchain-label/store_labels/etherscan/combinedAllLabels.json")
      result shouldBe a[Success[_]]
      result.get.size shouldBe 29945
            
    }
  }
}

class ListLoaderSpec extends AnyWordSpec with Matchers {
  
  "ListLoader" should {
    
    "load addresses with tags" in {
      val input = """0x123,name1,tag1;tag2
                    |0x456,name2,suspicious;exploit
                    |"0x789"""".stripMargin
      
      val loader = new ListLoader(input)
      val result = loader.load()
      
      result shouldBe a[Success[_]]
      val labels = result.get
      
      labels.size shouldBe 3
      
      // Check first address with tags
      val label1 = labels.find(_.addr == "0x123").get
      label1.tags shouldBe Set("tag1","tag2")
      
      // Check second address with tags
      val label2 = labels.find(_.addr == "0x456").get
      label2.tags shouldBe Set("suspicious","exploit")
      
      // Check third address without tags
      val label3 = labels.find(_.addr == "0x789").get
      label3.tags shouldBe Set()
    }
    
    "handle empty and blank lines" in {
      val input = """0x123,tag1
                    |
                    |0x456,tag2
                    |  
                    |0x789""".stripMargin
      
      val loader = new ListLoader(input)
      val result = loader.load()
      
      result shouldBe a[Success[_]]
      result.get.size shouldBe 3
    }
    
    "convert addresses to lowercase" in {
      val input = "0xABC123,tag1"
      
      val loader = new ListLoader(input)
      val result = loader.load()
      
      result shouldBe a[Success[_]]
      result.get.head.addr shouldBe "0xabc123"
    }

  }
}

class ExtLabelLoaderSpec extends AnyWordSpec with Matchers {
  
  "ExtLabelLoader" should {
    "load external labels from file" in {
      val testFile = "/tmp/ext-labels-test.txt"
      
      // Create test file with sample data
      os.write.over(
        os.Path(testFile, os.pwd), 
        """0x123,Exchange A,exchange;cex
          |0x456,DEX B,dex;amm
          |0x789,Wallet C,wallet
          |# Comment line to ignore
          |0xabc,Bridge D,bridge""".stripMargin
      )

      val loader = new ExtLabelLoader(testFile)
      val result = loader.load()
      
      result shouldBe a[Success[_]]
      val labels = result.get
      
      // Test basic loading
      labels.size shouldBe 4  // Should ignore comment line
      
      // Test specific label content
      val label1 = labels.find(_.addr == "0x123").get
      label1.name shouldBe Some("Exchange A")
      label1.tags should contain allOf ("exchange", "cex")
      label1.sid shouldBe Some("ext")
      
      val label2 = labels.find(_.addr == "0x456").get
      label2.name shouldBe Some("DEX B")
      label2.tags should contain allOf ("dex", "amm")
      
      val label3 = labels.find(_.addr == "0x789").get
      label3.name shouldBe Some("Wallet C")
      label3.tags should contain only "wallet"
      
      // Test case insensitivity
      val testAddr = "0xABC"
      labels.find(_.addr.toLowerCase == testAddr.toLowerCase) shouldBe defined
      
      // Cleanup
      os.remove(os.Path(testFile))
    }
    
    
  }
}
