package io.syspulse.skel.blockchain

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._

import scala.util.{Try,Success,Failure}
import java.time._
import scala.util.Random

import io.syspulse.skel.util.Util

class BlockchainSpec extends AnyWordSpec with Matchers {
  
  "BlockchainSpec" should {

    "parse 'ethereum'" in {
      val b = Blockchain("ethereum")
      b should ===(Blockchain.ETHEREUM)      
    }

    "parse 1 -> 'ethereum'" in {
      val b = Blockchain("1")
      b should ===(Blockchain.ETHEREUM)
    }
    
    "parse 'network:100' -> 'network':'100'" in {
      val b = Blockchain("network:100")
      b should ===(new Blockchain("network",Some("100")))
    }

    "parse 'base' -> 'Base'" in {
      val b = Blockchain("base")
      b should ===(Blockchain.BASE_MAINNET)     
    }

    "parse 'arbitrum' -> 'Arbitrum'" in {
      val b = Blockchain("base")
      b should ===(Blockchain.BASE_MAINNET)
      b.name should ===(Blockchain.BASE_MAINNET.name)
    }
    
    "parse 'unknown' as name" in {
      val b = Blockchain("unknown")      
      b.name should ===("unknown")
      b.id should === (None)
    }

    "parse '100' as chain_id" in {
      val b = Blockchain("100")      
      b.name should ===("")
      b.id should === (Some("100"))
    }

    "parse '' as no blockchain" in {
      val b = Blockchain("")
      b.name should ===("")
      b.id should === (None)      
    }

    "parse 'ethereum_sepolia' -> 'Sepolia'" in {
      Blockchain("ethereum_sepolia") should ===(Blockchain.ETHEREUM_SEPOLIA)
      Blockchain("sepolia") should ===(Blockchain.ETHEREUM_SEPOLIA)
    }

    "parse 'tron' -> 'Tron'" in {
      val b = Blockchain("tron")
      b should ===(Blockchain.TRON_MAINNET)      
    }

    "parse 'bsc_testnet' -> 'BSC_TESTNET'" in {
      val b = Blockchain("bsc_testnet")
      b should ===(Blockchain.BSC_TESTNET)
    }

    "resolve 'anvil' -> 'Anvil'" in {
      val b = Blockchain.resolve("anvil")
      b should ===(Some(Blockchain.ANVIL))
    }

    "get explorer URL for transaction" in {
      val txHash = "0x123456789abcdef"
      Blockchain.getExplorerTx(Some("ethereum"), txHash) should ===("https://etherscan.io/tx/0x123456789abcdef")
      Blockchain.getExplorerTx(Some("bsc"), txHash) should ===("https://bscscan.com/tx/0x123456789abcdef")
      Blockchain.getExplorerTx(Some("polygon"), txHash) should ===("https://polygonscan.com/tx/0x123456789abcdef")
      Blockchain.getExplorerTx(Some("zksync"), txHash) should ===("https://explorer.zksync.io/tx/0x123456789abcdef")
      Blockchain.getExplorerTx(Some("zeta"), txHash) should ===("https://explorer.zetachain.com/tx/0x123456789abcdef")
      Blockchain.getExplorerTx(Some("nonexistent"), txHash) should ===(txHash)
      Blockchain.getExplorerTx(None, txHash) should ===(txHash)
    }

    "get explorer URL for block" in {
      val blockNumber = "12345678"
      Blockchain.getExplorerBlock(Some("ethereum"), blockNumber) should ===("https://etherscan.io/block/12345678")
      Blockchain.getExplorerBlock(Some("bsc"), blockNumber) should ===("https://bscscan.com/block/12345678")
      Blockchain.getExplorerBlock(Some("polygon"), blockNumber) should ===("https://polygonscan.com/block/12345678")
      Blockchain.getExplorerBlock(Some("zksync"), blockNumber) should ===("https://explorer.zksync.io/block/12345678")
      Blockchain.getExplorerBlock(Some("zeta"), blockNumber) should ===("https://explorer.zetachain.com/block/12345678")
      Blockchain.getExplorerBlock(Some("nonexistent"), blockNumber) should ===(blockNumber)
      Blockchain.getExplorerBlock(None, blockNumber) should ===(blockNumber)
    }
    "resolve 'ethereum' when id = ''" in {
      val b = Blockchain.resolve(Blockchain("ethereum",Some("")))
      b should ===(Success(Blockchain.ETHEREUM.id.get.toLong))
    }

    "resolveByName should find existing blockchains" in {
      Blockchain.resolveByName("ethereum") should ===(Some(Blockchain.ETHEREUM))
      Blockchain.resolveByName("bsc") should ===(Some(Blockchain.BSC_MAINNET))
      Blockchain.resolveByName("arbitrum") should ===(Some(Blockchain.ARBITRUM_MAINNET))
      Blockchain.resolveByName("optimism") should ===(Some(Blockchain.OPTIMISM_MAINNET))
      Blockchain.resolveByName("polygon") should ===(Some(Blockchain.POLYGON_MAINNET))
      Blockchain.resolveByName("avalanche") should ===(Some(Blockchain.AVALANCHE_MAINNET))
      Blockchain.resolveByName("fantom") should ===(Some(Blockchain.FANTOM_MAINNET))
      Blockchain.resolveByName("scroll") should ===(Some(Blockchain.SCROLL_MAINNET))
      Blockchain.resolveByName("zksync") should ===(Some(Blockchain.ZKSYNC_MAINNET))
      Blockchain.resolveByName("polygon_zkevm") should ===(Some(Blockchain.POLYGON_ZKEVM_MAINNET))
      Blockchain.resolveByName("linea") should ===(Some(Blockchain.LINEA_MAINNET))
      Blockchain.resolveByName("base") should ===(Some(Blockchain.BASE_MAINNET))
      Blockchain.resolveByName("blast") should ===(Some(Blockchain.BLAST_MAINNET))
      Blockchain.resolveByName("telos") should ===(Some(Blockchain.TELOS_MAINNET))
      Blockchain.resolveByName("tron") should ===(Some(Blockchain.TRON_MAINNET))
      Blockchain.resolveByName("zeta") should ===(Some(Blockchain.ZETA_MAINNET))
      Blockchain.resolveByName("evm") should ===(Some(Blockchain.EVM))
    }

    "resolveByName should find test networks" in {
      Blockchain.resolveByName("bsc_testnet") should ===(Some(Blockchain.BSC_TESTNET))
      Blockchain.resolveByName("ethereum_sepolia") should ===(Some(Blockchain.ETHEREUM_SEPOLIA))
      Blockchain.resolveByName("anvil") should ===(Some(Blockchain.ANVIL))
      Blockchain.resolveByName("ethereum_holesky") should ===(Some(Blockchain.ETHEREUM_HOLESKY))
      Blockchain.resolveByName("polygon_amoy") should ===(Some(Blockchain.POLYGON_AMOY))
    }

    "resolveByName should handle case insensitive matching" in {
      Blockchain.resolveByName("ETHEREUM") should ===(Some(Blockchain.ETHEREUM))
      Blockchain.resolveByName("Ethereum") should ===(Some(Blockchain.ETHEREUM))
      Blockchain.resolveByName("ethereum") should ===(Some(Blockchain.ETHEREUM))
      Blockchain.resolveByName("BSC") should ===(Some(Blockchain.BSC_MAINNET))
      Blockchain.resolveByName("Bsc") should ===(Some(Blockchain.BSC_MAINNET))
      Blockchain.resolveByName("bsc") should ===(Some(Blockchain.BSC_MAINNET))
    }

    "resolveByName should handle whitespace trimming" in {
      Blockchain.resolveByName("  ethereum  ") should ===(Some(Blockchain.ETHEREUM))
      Blockchain.resolveByName("  bsc  ") should ===(Some(Blockchain.BSC_MAINNET))
      Blockchain.resolveByName("ethereum ") should ===(Some(Blockchain.ETHEREUM))
      Blockchain.resolveByName(" ethereum") should ===(Some(Blockchain.ETHEREUM))
    }

    "resolveByName should return None for non-existent blockchains" in {
      Blockchain.resolveByName("nonexistent") should ===(None)
      Blockchain.resolveByName("unknown") should ===(None)
      Blockchain.resolveByName("invalid") should ===(None)
      Blockchain.resolveByName("") should ===(None)
    }

    "resolveByName should handle special characters and edge cases" in {
      Blockchain.resolveByName("ethereum-test") should ===(None)
      Blockchain.resolveByName("ethereum_sepolia") should ===(Some(Blockchain.ETHEREUM_SEPOLIA))
      Blockchain.resolveByName("bsc_testnet") should ===(Some(Blockchain.BSC_TESTNET))
    }

    "should have correct native tokens for main networks" in {
      Blockchain.ETHEREUM.tok should ===(Some("ETH"))
      Blockchain.BSC_MAINNET.tok should ===(Some("BNB"))
      Blockchain.ARBITRUM_MAINNET.tok should ===(Some("ETH"))
      Blockchain.OPTIMISM_MAINNET.tok should ===(Some("ETH"))
      Blockchain.POLYGON_MAINNET.tok should ===(Some("POL"))
      Blockchain.AVALANCHE_MAINNET.tok should ===(Some("AVAX"))
      Blockchain.FANTOM_MAINNET.tok should ===(Some("FTM"))
      Blockchain.SCROLL_MAINNET.tok should ===(Some("ETH"))
      Blockchain.ZKSYNC_MAINNET.tok should ===(Some("ETH"))
      Blockchain.POLYGON_ZKEVM_MAINNET.tok should ===(Some("ETH"))
      Blockchain.LINEA_MAINNET.tok should ===(Some("ETH"))
      Blockchain.BASE_MAINNET.tok should ===(Some("ETH"))
      Blockchain.BLAST_MAINNET.tok should ===(Some("ETH"))
      Blockchain.TELOS_MAINNET.tok should ===(Some("TLOS"))
      Blockchain.TRON_MAINNET.tok should ===(Some("TRX"))
      Blockchain.ZETA_MAINNET.tok should ===(Some("ZETA"))
    }

    "should have correct native tokens for test networks" in {
      Blockchain.BSC_TESTNET.tok should ===(Some("BNB"))
      Blockchain.ETHEREUM_SEPOLIA.tok should ===(Some("ETH"))
      Blockchain.ETHEREUM_SEPOLIA.tok should ===(Some("ETH"))
      Blockchain.ANVIL.tok should ===(Some("ETH"))
      Blockchain.ETHEREUM_HOLESKY.tok should ===(Some("ETH"))
      Blockchain.POLYGON_AMOY.tok should ===(Some("POL"))
    }

    "should have correct decimals for main networks" in {
      Blockchain.ETHEREUM.dec should ===(Some(18))
      Blockchain.BSC_MAINNET.dec should ===(Some(18))
      Blockchain.ARBITRUM_MAINNET.dec should ===(Some(18))
      Blockchain.OPTIMISM_MAINNET.dec should ===(Some(18))
      Blockchain.POLYGON_MAINNET.dec should ===(Some(18))
      Blockchain.AVALANCHE_MAINNET.dec should ===(Some(18))
      Blockchain.FANTOM_MAINNET.dec should ===(Some(18))
      Blockchain.SCROLL_MAINNET.dec should ===(Some(18))
      Blockchain.ZKSYNC_MAINNET.dec should ===(Some(18))
      Blockchain.POLYGON_ZKEVM_MAINNET.dec should ===(Some(18))
      Blockchain.LINEA_MAINNET.dec should ===(Some(18))
      Blockchain.BASE_MAINNET.dec should ===(Some(18))
      Blockchain.BLAST_MAINNET.dec should ===(Some(18))
      Blockchain.TELOS_MAINNET.dec should ===(Some(18))
      Blockchain.TRON_MAINNET.dec should ===(Some(18))
      Blockchain.ZETA_MAINNET.dec should ===(Some(18))
    }

    "should have correct decimals for test networks" in {
      Blockchain.BSC_TESTNET.dec should ===(Some(18))
      Blockchain.ETHEREUM_SEPOLIA.dec should ===(Some(18))
      Blockchain.ETHEREUM_SEPOLIA.dec should ===(Some(18))
      Blockchain.ANVIL.dec should ===(Some(18))
      Blockchain.ETHEREUM_HOLESKY.dec should ===(Some(18))
      Blockchain.POLYGON_AMOY.dec should ===(Some(18))
    }

    "should have correct chain IDs for main networks" in {
      Blockchain.ETHEREUM.id should ===(Some("1"))
      Blockchain.BSC_MAINNET.id should ===(Some("56"))
      Blockchain.ARBITRUM_MAINNET.id should ===(Some("42161"))
      Blockchain.OPTIMISM_MAINNET.id should ===(Some("10"))
      Blockchain.POLYGON_MAINNET.id should ===(Some("137"))
      Blockchain.AVALANCHE_MAINNET.id should ===(Some("43114"))
      Blockchain.FANTOM_MAINNET.id should ===(Some("250"))
      Blockchain.SCROLL_MAINNET.id should ===(Some("534352"))
      Blockchain.ZKSYNC_MAINNET.id should ===(Some("324"))
      Blockchain.POLYGON_ZKEVM_MAINNET.id should ===(Some("1101"))
      Blockchain.LINEA_MAINNET.id should ===(Some("59144"))
      Blockchain.BASE_MAINNET.id should ===(Some("8453"))
      Blockchain.BLAST_MAINNET.id should ===(Some("238"))
      Blockchain.TELOS_MAINNET.id should ===(Some("40"))
      Blockchain.TRON_MAINNET.id should ===(Some("728126428"))
      Blockchain.ZETA_MAINNET.id should ===(Some("7000"))
    }

    "should have correct chain IDs for test networks" in {
      Blockchain.BSC_TESTNET.id should ===(Some("97"))
      Blockchain.ETHEREUM_SEPOLIA.id should ===(Some("11155111"))
      Blockchain.ETHEREUM_SEPOLIA.id should ===(Some("11155111"))
      Blockchain.ANVIL.id should ===(Some("31337"))
      Blockchain.ETHEREUM_HOLESKY.id should ===(Some("17000"))
      Blockchain.POLYGON_AMOY.id should ===(Some("80002"))
    }

  }    
}
