package io.syspulse.skel.blockchain

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import com.typesafe.config.ConfigFactory

class BlockchainRpcSpec extends AnyWordSpec with Matchers {

  "BlockchainRpc config" should {

    "read 'blockchains' from application-1.conf and parse ethereum, solana, base" in {
      val conf = ConfigFactory.load("application-1")
      val bb = conf.getString("blockchains")

      val rpc = BlockchainRpc.from(Seq(bb))

      rpc.get("1").map(_.name) should ===(Some("ethereum"))
      rpc.get("1").map(_.rpcUri) should ===(Some("https://eth.llamarpc.com"))

      rpc.get("8453").map(_.name) should ===(Some("base"))
      rpc.get("8453").map(_.rpcUri) should ===(Some("https://base.llamarpc.com"))

      rpc.get("0").map(_.name) should ===(Some("solana"))
      rpc.get("0").map(_.rpcUri) should ===(Some("https://api.mainnet-beta.solana.com"))
    }

    "read only 'rpc.solana' from application-1.conf and parse solana rpc" in {
      val conf = ConfigFactory.load("application-1")
      val bb = conf.getString("rpc.solana")

      val rpc = BlockchainRpc.from(Seq(bb))

      rpc.get("0").map(_.name) should ===(Some("solana"))
      rpc.get("0").map(_.rpcUri) should ===(Some("https://api.mainnet-beta.solana.com"))
    }
  }
}

