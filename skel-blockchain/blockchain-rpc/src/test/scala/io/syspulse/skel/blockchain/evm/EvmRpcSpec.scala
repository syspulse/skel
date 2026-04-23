package io.syspulse.skel.blockchain.evm

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.{Success,Failure}

class EvmRpcSpec extends AnyWordSpec with Matchers {
  
  "EvmRpcSpec" should {

    "parse multiline config" in {
      val bb = EvmBlockchains("""
      eth=1=https://eth.drpc.org,
      arb=42161=https://rpc.ankr.com/arbitrum,
      """)      
      
      info(s"bb: ${bb}")

      bb.all().size should ===(4)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
      
    }    

    "parse empty config" in {
      val bb = EvmBlockchains("")
      bb.all().size should ===(2) // default blockchains (anvil and sepolia)
      bb.get(1L) should ===(None)
      bb.get(11155111L) should !==(None) // sepolia
    }

    "parse single line config" in {
      val bb = EvmBlockchains("optimism=10=https://public-op-mainnet.fastnode.io")
      bb.all().size should ===(3) // default + optimism
      bb.get(10L) should !==(None)
      bb.getByName("optimism") should !==(None)
    }

    "handle unknown blockchain config gracefully" in {
      val bb = EvmBlockchains("malformed=config")
      // Current parser treats `rpc=id` as a valid entry (id becomes "config")
      bb.all().size should ===(3)
      bb.getByName("config") should !==(None)
    }

    "get blockchain by name" in {
      val bb = EvmBlockchains("""
      eth=1=https://eth.drpc.org,
      arb=42161=https://rpc.ankr.com/arbitrum,
      """)
      
      bb.getByName("eth") should !==(None)
      bb.getByName("arb") should !==(None)
      bb.getByName("nonexistent") should ===(None)
    }

    "get Web3j instance" in {
      val bb = EvmBlockchains("test=1=https://eth.llamarpc.com")
      bb.getWeb3(1L) shouldBe a[Success[_]]
      bb.getWeb3("test") shouldBe a[Success[_]]
      bb.getWeb3(999L) shouldBe a[Failure[_]]
      bb.getWeb3("nonexistent") shouldBe a[Failure[_]]

      bb.getWeb3("1".toLong) shouldBe a[Success[_]]
      bb.getWeb3("999".toLong) shouldBe a[Failure[_]]      
    }

    "handle multiple RPC URLs for same chain" in {
      val bb = EvmBlockchains("""
      eth=1=https://eth1.test.com,
      eth=1=https://eth2.test.com
      """)
      
      bb.all().size should ===(3) // last one should override
      bb.get(1L).map(_.rpcUri) should ===(Some("https://eth2.test.com"))
    }

    "support adding new blockchains" in {
      val bb = EvmBlockchains()
      bb.++(Seq("zksync=324=https://mainnet.era.zksync.io"))
      
      bb.all().size should ===(3)
      bb.get(324L) should !==(None)
      bb.getByName("zksync") should !==(None)
    }

    "ignore commented lines with #" in {
      val bb = EvmBlockchains("""
      # This is a comment
      eth=1=https://eth.drpc.org,
      # Another comment
      arb=42161=https://rpc.ankr.com/arbitrum,
      """)
      
      bb.all().size should ===(4)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
    }

    "ignore commented lines with //" in {
      val bb = EvmBlockchains("""
      // This is a comment
      eth=1=https://eth.drpc.org,
      // Another comment
      arb=42161=https://rpc.ankr.com/arbitrum,
      """)
      
      bb.all().size should ===(4)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
    }

    "ignore empty lines" in {
      val bb = EvmBlockchains("""
      eth=1=https://eth.drpc.org,
      
      arb=42161=https://rpc.ankr.com/arbitrum,
      
      """)
      
      bb.all().size should ===(4)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
    }

    "handle mixed comments and empty lines" in {
      val bb = EvmBlockchains("""
      # Mainnet RPCs
      eth=1=https://eth.drpc.org,
      
      // Testnet RPCs
      arb=42161=https://rpc.ankr.com/arbitrum,
      
      # Production RPC
      base=8453=https://mainnet.base.org,
      """)
      
      bb.all().size should ===(5)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
      bb.get(8453L) should !==(None)
      bb.getByName("eth") should !==(None)
      bb.getByName("arb") should !==(None)
      bb.getByName("base") should !==(None)
    }

    "drop inline comments that appear after a comma" in {
      val bb = EvmBlockchains("""
      eth=1=https://eth.drpc.org, # Mainnet
      arb=42161=https://rpc.ankr.com/arbitrum, // Arbitrum
      """)
      
      bb.all().size should ===(4)
      // Because we split by comma first, "# Mainnet" and "// Arbitrum" become separate entries
      // and are filtered out as comment-only lines.
      bb.get(1L).map(_.rpcUri.contains("# Mainnet")) should ===(Some(false))
      bb.get(42161L).map(_.rpcUri.contains("// Arbitrum")) should ===(Some(false))
    }
  }    
}
