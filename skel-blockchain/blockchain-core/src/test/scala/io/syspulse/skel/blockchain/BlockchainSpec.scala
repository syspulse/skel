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
  }    
}
