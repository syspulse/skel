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

class PriceSpec extends AnyWordSpec with Matchers {
  
  "PriceSpec" should {
    
    "Default Prices size" in {
      val sz = Price.size
      sz should === (3)
    }

    "resolve 'USDT' price" in {
      val t = Price.resolve("USDT")
      t.get.price should === (1.0)
    }

    "resolve 0xDAC17f958d2ee523a2206206994597c13d831ec7 -> '1.0'" in {
      val t = Price.resolve("0xDAC17f958d2ee523a2206206994597c13d831ec7")
      t.get.price should === (1.0) 
      //b should ===(Blockchain.ETHEREUM)      
    }
    
    "resolve price '0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599' (WBTC) -> >10000" in {
      val t = Price.resolve("0x2260FAC5E5542a773Aa44fBCfeDf7C193bc2C599")
      t should !== (None)
      t.get.price should === (89093.9628571238)
    }

    "resolve price (WBTC) " ignore {
      val t = Price.resolve("WETH")
      t should !== (None)
      t.get.price should === (89093.9628571238)
    }

    "resolve price '0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2' (WETH) -> " in {
      val t = Price.resolve("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
      t should !== (None)
      t.get.price should === (2424.41)
    }  

    "resolve price '0x1f9840a85d5af5bf1d1762f925bdaddc4201f984' (UNI) not in cache " in {
      val t = Price.resolve("0x1f9840a85d5af5bf1d1762f925bdaddc4201f984")
      info(s"${t}")
      t should !== (None)
      t.get.price > 1.0 should === (true)

      val t2 = Price.resolve("0x1f9840a85d5af5bf1d1762f925bdaddc4201f984")
      t2 should !== (None)
      t2.get.price > 1.0 should === (true)
    }  
      
    
  }    
}
