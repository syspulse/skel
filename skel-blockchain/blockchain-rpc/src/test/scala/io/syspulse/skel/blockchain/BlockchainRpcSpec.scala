package io.syspulse.skel.blockchain

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._

import scala.util.{Try,Success,Failure}
import java.time._
import io.syspulse.skel.crypto.Eth
import scala.util.Random
import io.syspulse.skel.util.Util
// import io.syspulse.skel.util.Util

class BlockchainRpcSpec extends AnyWordSpec with Matchers {
  
  "BlockchainRpcSpec" should {

    "parse multiline config" in {
      val bb = Blockchains("""
      eth=1=https://eth.drpc.org,
      arb=42161=https://rpc.ankr.com/arbitrum,
      """)      
      
      info(s"bb: ${bb}")

      bb.all().size should ===(4)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
      
    }    

    "parse empty config" in {
      val bb = Blockchains("")
      bb.all().size should ===(2) // default blockchains (ethereum and sepolia)
      bb.get(1L) should ===(None)
      bb.get(11155111L) should !==(None) // sepolia
    }

    "parse single line config" in {
      val bb = Blockchains("optimism=10=https://optimism-mainnet.public.blastapi.io")
      bb.all().size should ===(3) // default + optimism
      bb.get(10L) should !==(None)
      bb.getByName("optimism") should !==(None)
    }

    "handle unknown blockchain config gracefully" in {
      val bb = Blockchains("malformed=config")
      bb.all().size should ===(3) 
    }

    "get blockchain by name" in {
      val bb = Blockchains("""
      eth=1=https://eth.drpc.org,
      arb=42161=https://rpc.ankr.com/arbitrum,
      """)
      
      bb.getByName("eth") should !==(None)
      bb.getByName("arb") should !==(None)
      bb.getByName("nonexistent") should ===(None)
    }

    "get Web3j instance" in {
      val bb = Blockchains("test=1=http://localhost:8545")
      bb.getWeb3(1L) shouldBe a[Success[_]]
      bb.getWeb3("test") shouldBe a[Success[_]]
      bb.getWeb3(999L) shouldBe a[Failure[_]]
      bb.getWeb3("nonexistent") shouldBe a[Failure[_]]

      bb.getWeb3("1".toLong) shouldBe a[Success[_]]
      bb.getWeb3("999".toLong) shouldBe a[Failure[_]]      
    }

    "handle multiple RPC URLs for same chain" in {
      val bb = Blockchains("""
      eth=1=https://eth1.test.com,
      eth=1=https://eth2.test.com
      """)
      
      bb.all().size should ===(3) // last one should override
      bb.get(1L).map(_.rpcUri) should ===(Some("https://eth2.test.com"))
    }

    "support adding new blockchains" in {
      val bb = Blockchains()
      bb.++(Seq("zksync=324=https://mainnet.era.zksync.io"))
      
      bb.all().size should ===(3)
      bb.get(324L) should !==(None)
      bb.getByName("zksync") should !==(None)
    }
  }    
}
