package io.syspulse.skel.blockchain

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

class AddrSpec extends AnyWordSpec with Matchers {
  
  "AddrSpec" should {
    "create Addr with chain" in {
      val addr = Addr("0x52908400098527886E0F7030069857D2E4169EE7", Some("ethereum"))
      addr.addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      addr.chain should ===(Some("evm"))
      addr.toString should ===("evm:0x52908400098527886e0f7030069857d2e4169ee7")
    }

    "create Addr without chain" in {
      val addr = Addr("0x52908400098527886E0F7030069857D2E4169EE7", None)
      addr.addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      addr.chain should ===(None)
      addr.toString should ===("0x52908400098527886e0f7030069857d2e4169ee7")
    }

    "normalize address with chain" in {
      val (addr, chain) = Addr.normalize("ethereum:0x52908400098527886E0F7030069857D2E4169EE7")
      addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      chain should ===(Some("evm"))
    }

    "normalize address without chain" in {
      val (addr, chain) = Addr.normalize("0x52908400098527886E0F7030069857D2E4169EE7")
      addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      chain should ===(None)
    }

    "create Addr from string with chain" in {
      val addr = Addr("ethereum:0x52908400098527886E0F7030069857D2E4169EE7")
      addr.addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      addr.chain should ===(Some("evm"))
      addr.toString should ===("evm:0x52908400098527886e0f7030069857d2e4169ee7")
    }

    "handle whitespace in address" in {
      val addr = Addr("  ethereum:0x52908400098527886E0F7030069857D2E4169EE7  ")
      addr.addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      addr.chain should ===(Some("evm"))
      addr.toString should ===("evm:0x52908400098527886e0f7030069857d2e4169ee7")
    }

    "convert address to lowercase" in {
      val addr = Addr("ETHEREUM:0x52908400098527886E0F7030069857D2E4169EE7")
      addr.addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      addr.chain should ===(Some("evm"))
      addr.toString should ===("evm:0x52908400098527886e0f7030069857d2e4169ee7")
    }

    "handle empty chain" in {
      val addr = Addr(":0x52908400098527886E0F7030069857D2E4169EE7")
      addr.addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      addr.chain should ===(Some(""))
    }

    "handle multiple colons" in {
      val addr = Addr("ethereum:bsc:0x52908400098527886E0F7030069857D2E4169EE7")
      addr.addr should ===("bsc:0x52908400098527886E0F7030069857D2E4169EE7")
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
      val addr = Addr("ethereum:0x52908400098527886E0F7030069857D2E4169EE7")
      addr.addr should ===("0x52908400098527886e0f7030069857d2e4169ee7")
      addr.chain should ===(Some("evm"))
    }

    "compare EVM addresses case-insensitively and treat EVM chains identical" in {
      val a = Addr("base:0x52908400098527886E0F7030069857D2E4169EE7")
      val b = Addr("ethereum:0x52908400098527886e0f7030069857d2e4169ee7")
      a.==(b) should ===(true)
    }

    "compare EVM mixed-case addresses as equal" in {
      Addr.==(
        "ethereum:0xDeaDbeefdEAdbeefdEadbEEFdeadbeEFdEaDbeeF",
        "ethereum:0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef"
      ) should ===(true)
    }

    "compare Solana addresses case-sensitively" in {
      val raw = "7EcDhSYGxXyscszYEp35KHN8vvw3svAuLKTzXwCFLtV"
      val raw2 = raw.toLowerCase
      Addr.==(s"solana:$raw", s"solana:$raw2") should ===(false)
    }

    "compare Stellar addresses case-insensitively" in {
      val raw = "GCM5WPR4DDR24FSAX5LIEM4J7AI3KOWJYANSXEPKYXCSZOTAYXE75AFN"
      val raw2 = raw.toLowerCase
      Addr.==(s"stellar:$raw", s"stellar:$raw2") should ===(true)
    }

    "compare Starknet addresses case-insensitively but keep chain distinct from EVM" in {
      val raw = "0x02DdfB499765c064eaC5039E3841AA5f382E73B598097a40073BD8B48170Ab57"
      val raw2 = raw.toLowerCase
      Addr.==(s"starknet:$raw", s"starknet:$raw2") should ===(true)
      Addr.==(s"starknet:$raw", s"ethereum:$raw2") should ===(false)
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