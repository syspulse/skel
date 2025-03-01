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
      sz should === (5341)
    }

    "parse 'USDT'" in {
      val t = Token.resolve("USDT")
      t.size should === (9)
    }

    "parse 0xDAC17f958d2ee523a2206206994597c13d831ec7 -> 'USDT'" in {
      val t = Token.resolve("0xDAC17f958d2ee523a2206206994597c13d831ec7")
      t.head.sym should === ("USDT") 
      t.head.dec should === (6)
      t.head.bid should === ("ethereum")
      t.head.addr should === ("0xdac17f958d2ee523a2206206994597c13d831ec7")
      //b should ===(Blockchain.ETHEREUM)      
    }

    "parse 0x1111111111111111111111111111111111111111:TOKEN:12 -> None" in {
      val t = Token.resolve("0x1111111111111111111111111111111111111111:TOKEN:12")
      t should === (Set())
    }

    "parse 'USDC'" in {
      val t = Token.resolve("USDC")
      info(s"${t}")
      //t should === (Seq(Token.USDT)) 
      //b should ===(Blockchain.ETHEREUM)      
    }
    
    "parse '0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2'" in {
      val t = Token.find("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
      t should !== (None)
      // info(s"${t}")
    }
    
    "parse '0xb538d9f3e1Ae450827618519ACd96086Fc4C0a59'" in {
      val t = Token.find("0xb538d9f3e1Ae450827618519ACd96086Fc4C0a59")
      t should !== (None)
      // info(s"${t}")
    }

    "parse '0x44e18207b6e98f4a786957954e462ed46b8c95be'" in {
      val t = Token.find("0x44e18207b6e98f4a786957954e462ed46b8c95be")
      t should !== (None)      
    }

    "parse ''" in {
      val t = Token.find("0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2")
      t should !== (None)
      // info(s"${t}")
    }

  }    
}
