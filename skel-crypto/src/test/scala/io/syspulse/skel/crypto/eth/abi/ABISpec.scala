package io.syspulse.crypto.eth.abi

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
//import org.scalatest.TryValues._
import scala.io.Source

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash

import codegen.Decoder
import codegen.AbiDefinition
import io.syspulse.crypto.eth.Tx

import io.syspulse.skel.crypto.eth.abi._

class ABISpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath + "../../../"

  val abi = AbiStoreRepo.build().withRepo(new AbiStoreDir(s"${testDir}/abi/")).load().get

  "AbiRepo should load ABIs from abi/" in {
    abi.size should !== (0)    
  }

  "ABI should find UNI Contract in repo" in {
    val t = abi.find("0x1f9840a85d5af5bf1d1762f925bdaddc4201f984","transfer")
    info(s"t=${t}")
    t should !== (None)    
  }

  "ABI should find USDT Contract in repo" in {
    val t = abi.find("0xdac17f958d2ee523a2206206994597c13d831ec7","transfer")
    info(s"t=${t}")
    t should !== (None)
  }

  "Uniswap tx decoded with selector=transfer()" in {
    // https://etherscan.io/tx/0xc3292d77c6a2212a6f928ab005164a7ee113bf8b341b77a68fb777fa013fde98
    val tx = Tx(0L,0,"",0,
      fromAddress = "0x1d6b36dfb2b4f8648f0458197457622c8a9a94a7",
      toAddress = Option("0x1f9840a85d5af5bf1d1762f925bdaddc4201f984"),
      0L,BigInt(0),
      input = "0xa9059cbb000000000000000000000000f6bdeb12aba8bf4cfbfc2a60c805209002223e22000000000000000000000000000000000000000000000005a5a62f156c710000",
      value = BigInt(0))
    
    val selector = "transfer"
    val r = abi.decodeInput(tx.toAddress.get,tx.input,selector)
    
    info(s"$r")

    val s = r.get
    s.size shouldBe (2)
    s(0).toString shouldBe "(dst,address,0xf6bdeb12aba8bf4cfbfc2a60c805209002223e22)"
    s(1).toString shouldBe "(rawAmount,uint256,104170000000000000000)"
  }

  "Uniswap tx decoded with selector hash from input" in {
    // https://etherscan.io/tx/0xc3292d77c6a2212a6f928ab005164a7ee113bf8b341b77a68fb777fa013fde98
    val tx = Tx(0L,0,"",0,
      fromAddress = "0x1d6b36dfb2b4f8648f0458197457622c8a9a94a7",
      toAddress = Option("0x1f9840a85d5af5bf1d1762f925bdaddc4201f984"),
      0L,BigInt(0),
      input = "0xa9059cbb000000000000000000000000f6bdeb12aba8bf4cfbfc2a60c805209002223e22000000000000000000000000000000000000000000000005a5a62f156c710000",
      value = BigInt(0))
    
    val r = abi.decodeInput(tx.toAddress.get,tx.input,"")
    
    info(s"$r")

    val s = r.get
    s.size shouldBe (2)
    s(0).toString shouldBe "(dst,address,0xf6bdeb12aba8bf4cfbfc2a60c805209002223e22)"
    s(1).toString shouldBe "(rawAmount,uint256,104170000000000000000)"
  }

  "USDT tx decoded with selector hash from input (transfer)" in {
    // https://etherscan.io/tx/0x13380e6135ea3cf1ffcf22151e95c4341a16bf0d8d17f3befc0c4c5205546bf1
    val tx = Tx(0L,0,"",0,
      fromAddress = "0x974caa59e49682cda0ad2bbe82983419a2ecc400",
      toAddress = Option("0xdac17f958d2ee523a2206206994597c13d831ec7"),
      0L,BigInt(0),
      input = "0xa9059cbb0000000000000000000000006876dc741a44617fa7eb205cc5aa9dfbcc526a050000000000000000000000000000000000000000000000000000000001e84800",
      value = BigInt(0))
    
    val r = abi.decodeInput(tx.toAddress.get,tx.input,"")
    
    info(s"$r")

    val s = r.get
    s.size shouldBe (2)
    s(0).toString shouldBe "(_to,address,0x6876dc741a44617fa7eb205cc5aa9dfbcc526a05)"
    s(1).toString shouldBe "(_value,uint256,32000000)"
  }

  "USDT tx decoded with selector hash from input (transferFrom)" in {
    // https://etherscan.io/tx/0xaa27709c88aaa03fb4f81ab525c6d2a589237b3efc7e1dc4bea9d1571cee507a
    val tx = Tx(0L,0,"",0,
      fromAddress = "0xa152f8bb749c55e9943a3a0a3111d18ee2b3f94e",
      toAddress = Option("0xdac17f958d2ee523a2206206994597c13d831ec7"),
      0L,BigInt(0),
      input = "0x23b872dd000000000000000000000000b29c9f94d4c9ffa71876802196fb9b396bca631f000000000000000000000000ec30d02f10353f8efc9601371f56e808751f396f000000000000000000000000000000000000000000000000000000004b5eae1a",
      value = BigInt(0))
    
    val r = abi.decodeInput(tx.toAddress.get,tx.input,"")
    
    info(s"$r")

    val s = r.get
    s.size shouldBe (3)
    s(0).toString shouldBe "(_from,address,0xb29c9f94d4c9ffa71876802196fb9b396bca631f)"
    s(1).toString shouldBe "(_to,address,0xec30d02f10353f8efc9601371f56e808751f396f)"
    s(2).toString shouldBe "(_value,uint256,1264496154)"
  }

  "function 'totalSupply()'" in {
      val func = "totalSupply()"
      Util.hex(Hash.keccak256(func).take(4)) should === ("0x18160ddd")
    }
  "function 'balanceOf(address)'" in {
      val func = "balanceOf(address)"
      Util.hex(Hash.keccak256(func).take(4)) should === ("0x70a08231")
    }

}