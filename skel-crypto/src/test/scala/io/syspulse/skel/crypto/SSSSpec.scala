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

    

  }
}
