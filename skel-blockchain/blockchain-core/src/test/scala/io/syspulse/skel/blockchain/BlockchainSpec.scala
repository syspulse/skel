package io.syspulse.blockchain

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
      val b = Blockchain("ethereum_sepolia")
      b should ===(Blockchain.SEPOLIA)
      b should ===(Blockchain.ETHEREUM_SEPOLIA)
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
      Blockchain.getExplorerTx(Some("nonexistent"), txHash) should ===(txHash)
      Blockchain.getExplorerTx(None, txHash) should ===(txHash)
    }

    "get explorer URL for block" in {
      val blockNumber = "12345678"
      Blockchain.getExplorerBlock(Some("ethereum"), blockNumber) should ===("https://etherscan.io/block/12345678")
      Blockchain.getExplorerBlock(Some("bsc"), blockNumber) should ===("https://bscscan.com/block/12345678")
      Blockchain.getExplorerBlock(Some("polygon"), blockNumber) should ===("https://polygonscan.com/block/12345678")
      Blockchain.getExplorerBlock(Some("zksync"), blockNumber) should ===("https://explorer.zksync.io/block/12345678")
      Blockchain.getExplorerBlock(Some("nonexistent"), blockNumber) should ===(blockNumber)
      Blockchain.getExplorerBlock(None, blockNumber) should ===(blockNumber)
    }
    
  }    
}
