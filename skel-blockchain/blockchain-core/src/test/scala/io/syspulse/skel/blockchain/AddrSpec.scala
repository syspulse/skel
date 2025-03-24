package io.syspulse.blockchain

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

class AddrSpec extends AnyWordSpec with Matchers {
  
  "AddrSpec" should {
    "create Addr with chain" in {
      val addr = Addr("0x123", Some("ethereum"))
      addr.addr should ===("0x123")
      addr.chain should ===(Some("ethereum"))
      addr.toString should ===("ethereum:0x123")
    }

    "create Addr without chain" in {
      val addr = Addr("0x123", None)
      addr.addr should ===("0x123")
      addr.chain should ===(None)
      addr.toString should ===("0x123")
    }

    "normalize address with chain" in {
      val (addr, chain) = Addr.normalize("ethereum:0x123")
      addr should ===("0x123")
      chain should ===(Some("ethereum"))
    }

    "normalize address without chain" in {
      val (addr, chain) = Addr.normalize("0x123")
      addr should ===("0x123")
      chain should ===(None)
    }

    "create Addr from string with chain" in {
      val addr = Addr("ethereum:0x123")
      addr.addr should ===("0x123")
      addr.chain should ===(Some("ethereum"))
      addr.toString should ===("ethereum:0x123")
    }

    "handle whitespace in address" in {
      val addr = Addr("  ethereum:0x123  ")
      addr.addr should ===("0x123")
      addr.chain should ===(Some("ethereum"))
      addr.toString should ===("ethereum:0x123")
    }

    "convert address to lowercase" in {
      val addr = Addr("ETHEREUM:0xABCDEF")
      addr.addr should ===("0xabcdef")
      addr.chain should ===(Some("ethereum"))
      addr.toString should ===("ethereum:0xabcdef")
    }

    "handle empty chain" in {
      val addr = Addr(":0x123")
      addr.addr should ===("0x123")
      addr.chain should ===(Some(""))
    }

    "handle multiple colons" in {
      val addr = Addr("ethereum:bsc:0x123")
      addr.addr should ===("bsc:0x123")
      addr.chain should ===(Some("ethereum"))
    }

    "handle empty address" in {
      val addr = Addr("")
      addr.addr should ===("")
      addr.chain should ===(None)
      addr.toString should ===("")
    }
  }
} 