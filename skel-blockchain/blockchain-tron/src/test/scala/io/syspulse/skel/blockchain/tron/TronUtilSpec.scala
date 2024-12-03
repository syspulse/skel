package io.syspulse.skel.blockchain.tron

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class TronUtilSpec extends AnyWordSpec with Matchers {
  
  "TronUtilSpec" should {    
    "encode Eth addresses" in {
      TronUtil.ethToTron("0xb538d9f3e1Ae450827618519ACd96086Fc4C0a59") should === ("TSVRQtBaFb7QLuDzDUjY7BNjjso3X8yUA3")
      TronUtil.ethToTron("0x790d26621439142b61a829c53518a1ca0877bafd") should === ("TM1GWwhbB6swu2qWxf7RarbvptcCB4XeBQ")
    }

    "encode Eth address: 0x00000000000000000000000000000000000000001" ignore {
      val a1 = TronUtil.ethToTron("0x0000000000000000000000000000000000000001")            
      a1 should === ("TSVRQtBaFb7QLuDzDUjY7BNjjso3X8yUA3")      
    }
    
  }
}
