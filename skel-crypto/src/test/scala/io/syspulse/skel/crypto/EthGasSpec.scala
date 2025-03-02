package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthGasSpec extends AnyWordSpec with Matchers with TestData {

  implicit val web3 = Eth.web3(rpcUri="https://eth-sepolia.public.blastapi.io")

  "EthGasSpec" should {
    "fail to get current gas on invalid RPC" in {
      val g1 = Eth.getGasPrice("http://localhost:18545")
      info(s"${g1}")
      g1 shouldBe a [Failure[_]]
    }

    "get current gas on remote RPC" in {
      val g1 = Eth.getGasPrice()
      info(s"${g1.get} wei (${g1.get.toDouble / 1000_000_000.0} gwei)")
      g1 shouldBe a [Success[_]]
      g1.get should !== (0L)
    }

    "convert 20 gwei to wei" in {
      val v1 = Eth.strToWei("20 gwei")
      v1 should === (Success(BigInt("20000000000")))
    }

    "convert 20 to 20 wei" in {
      val v1 = Eth.strToWei("20 wei")
      v1 should === (Success(BigInt("20")))
    }

    "convert '20 Eth' to wei" in {
      val v1 = Eth.strToWei("20 Eth")      
      v1 should === (Success(BigInt("20000000000000000000")))
    }

    "convert '20 Ether' to wei" in {
      val v1 = Eth.strToWei("20 Ether")
      v1 should === (Success(BigInt("20000000000000000000")))
    }

    "convert 'current' to wei" in {
      val v1 = Eth.strToWei("current")
      info(s"current price: ${v1}")
      v1 should !== (Success(BigInt(0)))
    }

    "percentage 20% of 100 should be 20" in {
      val v1 = Eth.percentageToWei(100,"20%")
      v1 should === (20.0)
    }

    "convert '25.0%' to 25% of the current" in {
      val v1 = Eth.strToWei("current")      
      val v2 = Eth.strToWei("25.0%")
      v1 shouldBe a [Success[_]]
      v2 shouldBe a [Success[_]]

      val diff = (v2.get.toDouble / v1.get.toDouble *100.0).ceil
      info(s"current=${v1}, 25%=${v2}: diff=${diff}")

      v2.get < v1.get should === (true)
      (diff).toLong should === (25)
    }

    "convert '+25.0%' to +25% higher than current" in {
      val v1 = Eth.strToWei("current")      
      val v2 = Eth.strToWei("+25.0%")
      v1 shouldBe a [Success[_]]
      v2 shouldBe a [Success[_]]

      val diff = (v2.get.toDouble / v1.get.toDouble *100.0).ceil
      info(s"current=${v1}, +25%=${v2}: diff=${diff}")

      v2.get > v1.get should === (true)
      (diff).toLong should === (100 + 25)
    }

    "convert '150.0%' to 150% of the current" in {
      val v1 = Eth.strToWei("current")      
      val v2 = Eth.strToWei("150%")
      v1 shouldBe a [Success[_]]
      v2 shouldBe a [Success[_]]

      val diff = (v2.get.toDouble / v1.get.toDouble *100.0).ceil
      info(s"current=${v1}, 150%=${v2}: diff=${diff}")

      v2.get > v1.get should === (true)
      (diff).toLong should === (150)
    }
    
    "convert '-25.0%' to lower than current price" in {
      val v1 = Eth.strToWei("current")   
      val v2 = Eth.strToWei("-25.0%")
      v1 shouldBe a [Success[_]]
      v2 shouldBe a [Success[_]]

      val diff = (v2.get.toDouble / v1.get.toDouble *100.0).ceil
      info(s"current=${v1}, -25%=${v2}: diff=${diff}")

      v2.get < v1.get should === (true)
      (diff).toLong should === (100 - 25)
    }

     "'-25.0%' of 1 gwei must be 0" in {
      val v1 = Eth.percentageToWei(1,"-25.0%")
      v1 < 1.0 should === (true)
            
    }

    "'25.0%' of 1 gwei must be >0 and <1" in {
      val v1 = Eth.percentageToWei(1,"25.0%")
      v1 > 0.0 && v1 < 1.0 should === (true)
    }

    "'-10000.0%' of 1 gwei must be > 0" in {
      val v1 = Eth.percentageToWei(1,"-10000.0%")
      v1 >= 0.0 should === (true)            
    }

    "percentage 0% of 100 should be 0" in {
      val v1 = Eth.percentageToWei(100,"0%")
      v1 should === (0.0)
    }

    "percentage 100% of 100 should be 100" in {
      val v1 = Eth.percentageToWei(100,"100%")
      v1 should === (100.0)
    }

    "percentage 200% of 100 should be 200" in {
      val v1 = Eth.percentageToWei(100,"200%")
      v1 should === (200.0)
    }

    "percentage +50% of 100 should be 150" in {
      val v1 = Eth.percentageToWei(100,"+50%")
      v1 should === (150.0)
    }

    "percentage with decimal 33.33% of 100 should be 33.33" in {
      val v1 = Eth.percentageToWei(100,"33.33%")
      v1 should === (33.33)
    }

    "percentage with spaces ' 50% ' should work" in {
      val v1 = Eth.percentageToWei(100," 50% ")
      v1 should === (50.0)
    }
    
    "empty percentage string should fail" in {
      assertThrows[IllegalArgumentException] {
        Eth.percentageToWei(100,"")
      }
    }

  }
}
