package io.syspulse.blockchain

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

      //info(s"${bb.all()}")
      
      bb.all().size should ===(4)
      bb.get(1L) should !==(None)
      bb.get(42161L) should !==(None)
      
    }    
  }    
}
