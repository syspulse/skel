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

class TokenSpec extends AnyWordSpec with Matchers {
  
  "TokenSpec" should {
    
    "Default TOKENS size" in {
      val sz = Token.size
      sz should === (9404)
    }

    "parse 'USDT'" in {
      val t = Token.resolve("USDT")
      t.size should === (33)
    }

    "parse 0xDAC17f958d2ee523a2206206994597c13d831ec7 -> 'USDT'" in {
      val t = Token.resolve("0xDAC17f958d2ee523a2206206994597c13d831ec7")
      t should === (Set(Token.USDT)) 
      //b should ===(Blockchain.ETHEREUM)      
    }

    "parse 0x1111111111111111111111111111111111111111:TOKEN:12 -> 'TOKEN.12'" in {
      val t = Token.resolve("0x1111111111111111111111111111111111111111:TOKEN:12")
      t should === (Set(Token("0x1111111111111111111111111111111111111111","TOKEN",12,"")))
    }

    
    "parse 'USDC'" in {
      val t = Token.resolve("USDC")
      info(s"${t}")
      //t should === (Seq(Token.USDT)) 
      //b should ===(Blockchain.ETHEREUM)      
    }
    
  }    
}
