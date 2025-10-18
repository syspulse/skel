package io.syspulse.skel.blockchain.eth

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthUtilSpec extends AnyWordSpec with Matchers {
  
  "EthUtilSpec" should {
    
    "parse UniswapV2 Swap event" in {
      val s1 = EthUtil.decodeSwap(
        "0x000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000003782dace9d90000000000000000000000000000000000000000000000000000004b0c654d3aad1a0000000000000000000000000000000000000000000000000000000000000000",
        Array("0xd78ad95fa46c994b6551d0da85fc275fe613ce37657fb8d5e3d130840159d822","0x0000000000000000000000003fc91a3afd70395cd496c647d5a6cc9d4b2b7fad","0x0000000000000000000000000c9f04c7cefc265c549891eff8641f93a3c599df"))
      
      s1 shouldBe a [Some[_]]
      info(s"swap=${s1}")
      
      s1.get.sender should === ("0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD".toLowerCase())
      s1.get.to should === ("0x0C9F04c7CEfC265c549891efF8641F93a3c599DF".toLowerCase())
      
      s1.get.amount0In should === (0)
      s1.get.amount1In should === (BigInt("250000000000000000"))
      s1.get.amount0Out should === (BigInt("21124252480220442"))
      s1.get.amount1Out should === (0)
    }

    "parse UniswapV4 Swap event" in {
      val s1 = EthUtil.decodeSwap(
        "0x0000000000000000000000000000000000000000000000000000003369161fa5fffffffffffffffffffffffffffffffffffffffffffffffffffffffec5b46f56000000000000000000000000000000000000000024f290b24d82cfae84e0bea10000000000000000000000000000000000000000000000000000003a0a0b282affffffffffffffffffffffffffffffffffffffffffffffffffffffffffff68c4",
        Array("0xc42079f94a6350d7e6235f29174924f928cc2ac818eb64fed8004e115fbcca67","0x000000000000000000000000e592427a0aece92de3edee1f18e0157c05861564","0x0000000000000000000000005a89d0400ab44bf82dc39f54ed4943d40906ec5d"))
      
      s1 shouldBe a [Some[_]]
      info(s"swap=${s1}")
      
      s1.get.sender should === ("0xe592427a0aece92de3edee1f18e0157c05861564".toLowerCase())
      s1.get.to should === ("0x5a89d0400ab44bf82dc39f54ed4943d40906ec5d".toLowerCase())
      
      s1.get.amount0In should === (0)
      s1.get.amount1In should === (BigInt("220806389669"))
      s1.get.amount0Out should === (BigInt("5272998058"))
      s1.get.amount1Out should === (0)
    }
    
  }
}
