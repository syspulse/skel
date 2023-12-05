package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthNonceSpec extends AnyWordSpec with Matchers with TestData {

  implicit val web3 = Eth.web3(rpcUri="https://eth-sepolia.public.blastapi.io")

  "EthNonceSpec" should {
    
    "get current nonce for address" in {
      val n1 = Eth.getNonce("0xc783df8a850f42e7F7e57013759C285caa701eB6")
      n1 shouldBe a [Success[_]]
      info(s"nonce=${n1}")
      n1.get should !== (0L)
    }
    
  }
}
