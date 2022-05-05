package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class ShamirKeysFlowSpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  val bsk1 = h"0x4130aea3da34b3b5a03305690aaa3bd3a1490e39812f98f0e96b1827929be363"
  val bpk1 = h"0x8dfd66bcf262f69c5fd0ebeb266188c1526137091a33c5f0a02b388289ba6afaf92d814396d3260fc6eca05387e46bd7"

  "ShamirKeysFlowSpec" should {
    
    "generate 5 shares of PrivateKey and sign with first-3 and verify with last-3" in {
      val k1 = Eth2.generate(bsk1)
      val s1 = SSS.createShares(k1.sk,3,5)
      s1 shouldBe a [Success[_]]
      s1.get.size === (5)

      val data = "message1"

      // sign with 3 first shares
      val sk2 = SSS.getSecret(s1.get.take(3))
      sk2 === (h"0x4130aea3da34b3b5a03305690aaa3bd3a1490e39812f98f0e96b1827929be363")
      val sig1 = Eth2.sign(sk2.get,data)

      // sign with 3 last shares
      val sk3 = SSS.getSecret(s1.get.drop(2).take(3))
      sk3 === (h"0x4130aea3da34b3b5a03305690aaa3bd3a1490e39812f98f0e96b1827929be363")
      val sig2 = Eth2.sign(sk3.get,data)

      // sigs are different
      sig1 should === (sig2)

      val pk3 = bpk1

      // verify sig1
      val v1 = Eth2.verify(pk3,data,sig1)
      v1 should === (true)

      val v2 = Eth2.verify(pk3,data,sig2)
      v2 should === (true)
    }
    
  }
}
