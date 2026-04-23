package io.syspulse.skel.blockchain

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

    // Solana addresses are base58, case-sensitive
    "preserve case for Solana address" in {
      val raw = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"
      val addr = Addr(raw, Some("solana"))
      addr.addr should ===(raw)
      addr.chain should ===(Some("solana"))
      addr.toString should ===(s"solana:$raw")
    }

    "preserve case for Solana address from string" in {
      val raw = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"
      val addr = Addr(s"solana:$raw")
      addr.addr should ===(raw)
      addr.chain should ===(Some("solana"))
    }

    // Stellar addresses are base32 strkey, case-sensitive (always uppercase by spec, but must not be altered)
    "preserve case for Stellar address" in {
      val raw = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"
      val addr = Addr(raw, Some("stellar"))
      addr.addr should ===(raw)
      addr.chain should ===(Some("stellar"))
      addr.toString should ===(s"stellar:$raw")
    }

    "preserve case for Stellar address from string" in {
      val raw = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"
      val addr = Addr(s"stellar:$raw")
      addr.addr should ===(raw)
      addr.chain should ===(Some("stellar"))
    }

    // Tron addresses are base58check, case-sensitive (start with 'T')
    "preserve case for Tron address" in {
      val raw = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"
      val addr = Addr(raw, Some("tron"))
      addr.addr should ===(raw)
      addr.chain should ===(Some("tron"))
      addr.toString should ===(s"tron:$raw")
    }

    "preserve case for Tron address from string" in {
      val raw = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"
      val addr = Addr(s"tron:$raw")
      addr.addr should ===(raw)
      addr.chain should ===(Some("tron"))
    }

    // Bitcoin addresses are base58check, case-sensitive
    "preserve case for Bitcoin address" in {
      val raw = "1A1zP1eP5QGefi2DMPTfTL5SLmv7Divf"
      val addr = Addr(raw, Some("bitcoin"))
      addr.addr should ===(raw)
      addr.chain should ===(Some("bitcoin"))
      addr.toString should ===(s"bitcoin:$raw")
    }

    "preserve case for Bitcoin address from string" in {
      val raw = "1A1zP1eP5QGefi2DMPTfTL5SLmv7Divf"
      val addr = Addr(s"bitcoin:$raw")
      addr.addr should ===(raw)
      addr.chain should ===(Some("bitcoin"))
    }

    // EVM addresses still get lowercased via normalize
    "still lowercase EVM 0x address via normalize" in {
      val addr = Addr("ethereum:0xABCDEF123")
      addr.addr should ===("0xabcdef123")
      addr.chain should ===(Some("ethereum"))
    }

    "preserve case for bare Solana address (no chain, no prefix)" in {
      val raw = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"
      val addr = Addr(raw)
      addr.addr should ===(raw)
      addr.chain should ===(None)
      addr.toString should ===(raw)
    }

    "preserve case for bare Stellar address (no chain, no prefix)" in {
      val raw = "GAAZI4TCR3TY5OJHCTJC2A4QSY6CJWJH5IAJTGKIN2ER7LBNVKOCCWN"
      val addr = Addr(raw)
      addr.addr should ===(raw)
      addr.chain should ===(None)
      addr.toString should ===(raw)
    }

    "preserve case for bare Tron address (no chain, no prefix)" in {
      val raw = "TJRabPrwbZy45sbavfcjinPJC18kjpRTv8"
      val addr = Addr(raw)
      addr.addr should ===(raw)
      addr.chain should ===(None)
      addr.toString should ===(raw)
    }

    "preserve case for bare Bitcoin address (no chain, no prefix)" in {
      val raw = "1A1zP1eP5QGefi2DMPTfTL5SLmv7Divf"
      val addr = Addr(raw)
      addr.addr should ===(raw)
      addr.chain should ===(None)
      addr.toString should ===(raw)
    }
  }
} 