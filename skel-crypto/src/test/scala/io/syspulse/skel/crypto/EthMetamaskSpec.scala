package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util
import org.web3j.crypto.Sign
import org.web3j.crypto.Sign._
import org.web3j.crypto
import java.math.BigInteger
import java.nio.charset.StandardCharsets

class EthMetamaskSpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "EthMetamask" should {
    
    "sign message 'test' " in {
      val kk1 = Eth.generate("0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db")

      val sig1 = Eth.signMetamask("test",kk1.get)
      info(s"sig1: ${sig1}")
      Util.hex(sig1.r) === ("0x2c11b15223fa3d9f8320049dc1a576f4c55bd40a3bd3b9760f5cf1a608c8a55a")
      Util.hex(sig1.s) === ("0x43d0315421a39451e13f23767a1a1f4c2b1d7544fdaf9da5ac94982410e61aa8")
      sig1.v === (0x1c)
    }

    "sign and revover message 'test' (Metamask) " in {
      val kk1 = Eth.generate("0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db")
      val addr = "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1".toLowerCase()

      info(s"addr: ${Eth.address(kk1.get.pk)}")
      info(s"pk: ${Util.hex(kk1.get.pk)}")
      Eth.address(kk1.get.pk) === (addr)

      val sig1 = Eth.signMetamask("test",kk1.get)
      info(s"sig1: ${sig1}")
      Util.hex(sig1.r) === ("0x2c11b15223fa3d9f8320049dc1a576f4c55bd40a3bd3b9760f5cf1a608c8a55a")
      Util.hex(sig1.s) === ("0x43d0315421a39451e13f23767a1a1f4c2b1d7544fdaf9da5ac94982410e61aa8")

      val pk2 = Eth.recoverMetamask("test",sig1).get
      info(s"recovered: pk=${Util.hex(pk2)}, addr=${Eth.address(pk2)}")

      Eth.address(pk2) === (Eth.address(kk1.get.pk))
    }

    "recover address from Wallet Signature' " in {
      // adderess: 0x0186c7E33411617c03bc5AaA68642fFC6c60Fc8b
      // message: 'test'
      // sig: 0xd154fd4171820e35a1cf48e67242779714d176e59e19de02dcf62b78cd75946d0bd46da493810b66b589667286d05c0f4e1b0cc6f29a544361ad639b0a6614041c

      val data = h"0xd154fd4171820e35a1cf48e67242779714d176e59e19de02dcf62b78cd75946d0bd46da493810b66b589667286d05c0f4e1b0cc6f29a544361ad639b0a6614041c"
      val sig =  SignatureEth(data)
      info(s"${sig}")
      
      val aa = Eth.recoverMetamask("test",sig).get
      info(s"recovered: pk=${Util.hex(aa)}, addr=${Eth.address(aa)}")
    }
  }
}
