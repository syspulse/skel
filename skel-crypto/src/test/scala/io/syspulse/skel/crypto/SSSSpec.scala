package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class SSSSpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "SSS" should {
    
    "generate 5 shares" in {
      val sss = SSS.createShares("secret data",3,5)
      sss shouldBe a [Success[_]]
      sss.get.size === (5)
    }

    "recover secret from 5 shares of 5" in {
      val sss = SSS.createShares("secret data",3,5)
      sss shouldBe a [Success[_]]
      val d1 = SSS.getSecret(sss.get)
      d1 === ("secret data")
    }

    "recover secret from 3 first shares of 5" in {
      val sss = SSS.createShares("secret data",3,5)
      sss shouldBe a [Success[_]]
      val d1 = SSS.getSecret(sss.get.take(3))
      d1 === ("secret data")
    }

    "recover secret from 3 last shares of 5" in {
      val sss = SSS.createShares("secret data",3,5)
      sss shouldBe a [Success[_]]
      val d1 = SSS.getSecret(sss.get.drop(2))
      d1 === ("secret data")
    }

    "NOT recover secret from 2 shares of 5" in {
      val sss = SSS.createShares("secret data",3,5)
      sss shouldBe a [Success[_]]
      val d1 = SSS.getSecret(sss.get.take(2))
      d1 !== ("secret data")
    }

    "NOT recover secret from 1 shares of 5" in {
      val sss = SSS.createShares("secret data",3,5)
      sss shouldBe a [Success[_]]
      val d1 = SSS.getSecret(sss.get.take(1))
      d1 !== ("secret data")
    }

    "NOT recover secret from 0 shares" in {
      val sss = SSS.createShares("secret data",3,5)
      sss shouldBe a [Success[_]]
      val d1 = SSS.getSecret(List())
      d1 !== ("secret data")
    }

    
    "shares of 3:3 must be unique and prime the same" in {
      val sss1 = SSS.createShares("secret_1",3,3)
      sss1 shouldBe a [Success[_]]
      
      for (i <- 0 until 3) {
        info(s"sss1($i): x=${sss1.get(i).x}, y=${sss1.get(i).y}, prime=${sss1.get(i).primeUsed}")      
      }      
      sss1.get(0).y !== sss1.get(1).y
      sss1.get(0).y !== sss1.get(2).y
      sss1.get(1).y !== sss1.get(2).y

      sss1.get(0).primeUsed === sss1.get(1).primeUsed      
      sss1.get(0).primeUsed === sss1.get(2).primeUsed      
      sss1.get(1).primeUsed === sss1.get(2).primeUsed      
    }

    "different shares of 3:3 for the same secret must be unique" in {
      val secret = "secret_1"
      val sss1 = SSS.createShares(secret,3,3)
      sss1 shouldBe a [Success[_]]
      val sss2 = SSS.createShares(secret,3,3)
      sss2 shouldBe a [Success[_]]
      
      // for (i <- 0 until 3) {
      //   info(s"sss1($i): x=${sss1.get(i).x}, y=${sss1.get(i).y}, prime=${sss1.get(i).primeUsed}")
      //   info(s"sss2($i): x=${sss2.get(i).x}, y=${sss2.get(i).y}, prime=${sss2.get(i).primeUsed}")
      // }
      sss1.get(0).y !== sss2.get(0).y
      sss1.get(1).y !== sss2.get(1).y
      sss1.get(2).y !== sss2.get(2).y

      sss1.get(0).primeUsed !== sss2.get(0).primeUsed      
      
    }

  }
}
