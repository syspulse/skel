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
      sz should === (10367)
    }

    "parse 'USDT'" in {
      val t = Token.resolve("USDT")
      t.size should === (64)
    }

    "parse 0xDAC17f958d2ee523a2206206994597c13d831ec7 -> 'USDT'" in {
      val t = Token.resolve("0xDAC17f958d2ee523a2206206994597c13d831ec7")
      t.head should === (Token.USDT) 
      //b should ===(Blockchain.ETHEREUM)      
    }

    "parse 0x1111111111111111111111111111111111111111:TOKEN:12 -> 'TOKEN.12'" in {
      val t = Token.resolve("0x1111111111111111111111111111111111111111:TOKEN:12")
      t should === (Set(Token("0x1111111111111111111111111111111111111111","TOKEN",12,"")))
    }

    
    "parse 'USDC'" in {
      val t = Token.resolve("USDC")
      //t should === (Seq(Token.USDT)) 
      //b should ===(Blockchain.ETHEREUM)      
    }
    
    
    
    "parse '0xb538d9f3e1Ae450827618519ACd96086Fc4C0a59'" in {
      val t = Token.find("0xb538d9f3e1Ae450827618519ACd96086Fc4C0a59")
      t should !== (None)
      info(s"${t}")
    }

    "parse '0x44e18207b6e98f4a786957954e462ed46b8c95be'" in {
      val t = Token.find("0x44e18207b6e98f4a786957954e462ed46b8c95be")
      t should === (None)      
    }

  }    
}
