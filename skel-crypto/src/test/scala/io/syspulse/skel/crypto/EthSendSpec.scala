package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthSendSpec extends AnyWordSpec with Matchers with TestData {

  // Run 'anvil' befire running tests !
  "EthSendSpec" should {
    
    "send raw transaction without data" in {
      val r = Eth.transaction(
        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80",
        "0xa0Ee7A142d267C1f36714E4a8F75612F20a79720",
        value = "10",
        gasPrice = "20.0",
        gasTip = "5.0",
        gasLimit = 21000L,
        data = None,
        chainId = 31337,
        rpcUri = "http://localhost:8545"
      )
      
      info(s"r=${r}")
      r.isFailure should === (false)
    }
    
  }
}
