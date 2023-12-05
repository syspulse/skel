package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthGasSpec extends AnyWordSpec with Matchers with TestData {

  "EthGasSpec" should {    
    "fail get current gas on invalid RPC" in {
      val g1 = Eth.gasPrice()
      info(s"${g1}")
      g1 shouldBe a [Failure[_]]
    }

    "get current gas on remote RPC" in {
      val g1 = Eth.gasPrice(chainId = 11155111L, rpcUri="https://eth-sepolia.public.blastapi.io")
      info(s"${g1.get / 1000_000_000.0} gwei")
      g1 shouldBe a [Success[_]]
      g1.get !== (0L)
    }
    
  }
}
