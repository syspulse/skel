package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthGasSpec extends AnyWordSpec with Matchers with TestData {

  implicit val web3 = Eth.web3(rpcUri="https://eth-sepolia.public.blastapi.io")

  "EthGasSpec" should {
    "fail to get current gas on invalid RPC" in {
      val g1 = Eth.gasPrice("http://localhost:8545")
      info(s"${g1}")
      g1 shouldBe a [Failure[_]]
    }

    "get current gas on remote RPC" in {
      val g1 = Eth.gasPrice()
      info(s"${g1.get} wei (${g1.get.toDouble / 1000_000_000.0} gwei)")
      g1 shouldBe a [Success[_]]
      g1.get should !== (0L)
    }

    "convert 20 gwei to wei" in {
      val v1 = Eth.strToWei("20 gwei")
      v1 should === (BigInt("20000000000"))
    }

    "convert 20 to 20 wei" in {
      val v1 = Eth.strToWei("20 wei")
      v1 should === (BigInt("20"))                             
    }

    "convert '20 Eth' to wei" in {
      val v1 = Eth.strToWei("20 Eth")      
      v1 should === (BigInt("20000000000000000000"))
    }

    "convert '20 Ether' to wei" in {
      val v1 = Eth.strToWei("20 Ether")
      v1 should === (BigInt("20000000000000000000"))
    }

    "convert 'current' to wei" in {
      val v1 = Eth.strToWei("current")
      info(s"current price: ${v1}")
      v1 should !== (0)
    }
    
  }
}
