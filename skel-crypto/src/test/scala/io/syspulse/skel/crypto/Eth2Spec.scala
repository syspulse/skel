package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class Eth2Spec extends AnyWordSpec with Matchers with TestData {
  import Util._

  val bsk1 = h"0x4130aea3da34b3b5a03305690aaa3bd3a1490e39812f98f0e96b1827929be363"
  val bpk1 = h"0x8dfd66bcf262f69c5fd0ebeb266188c1526137091a33c5f0a02b388289ba6afaf92d814396d3260fc6eca05387e46bd7"
  
  val bpk2 = h"0x8dfd66bcf262f69c5fd0ebeb266188c1526137091a33c5f0a02b388289ba6afaf92d814396d3260fc6eca05387e46bd0"

  val bsk3 = h"0x58ac0936141cec69039407b4d5a542d9d2f2dfb3c8333a5e9a6fef13c8128d96"
  val bpk3 = h"0x8ef198daa0f0659d2127164aa52b883a1c50324367dd0e03fa9d591af3722097d030123247d3b135dceaffe9c62673d7"
  
  val bsk4 = h"0x0c574ea69ad41121fbfcd98052e6990e4e6e65b1dff42dfd66c5243ac4c04cf6"
  val bpk4 = h"0x97fc5427ff2d5fd8dd90eeb7a03079a6996faf535bbb519ba906b52793d0040566df639d13f820edc8a94adf41053127"
  

  "Eth2" should {
    "generate KeyPair from sk" in {
      val k1 = Eth2.generate(bsk1).get
      k1.sk should === (h"0x4130aea3da34b3b5a03305690aaa3bd3a1490e39812f98f0e96b1827929be363")
      k1.pk should === (h"0x8dfd66bcf262f69c5fd0ebeb266188c1526137091a33c5f0a02b388289ba6afaf92d814396d3260fc6eca05387e46bd7")

      val k3 = Eth2.generate(bsk3).get
      k3.sk should === (h"0x58ac0936141cec69039407b4d5a542d9d2f2dfb3c8333a5e9a6fef13c8128d96")
      k3.pk should === (h"0x8ef198daa0f0659d2127164aa52b883a1c50324367dd0e03fa9d591af3722097d030123247d3b135dceaffe9c62673d7")
    }

    "generate SK from 12 words mnemonic" in {
      val k1 = Eth2.generate("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat")
      k1.get.sk should === (h"0x69e0debb3512b0000cabddb5fdce0d10b2b08d03e1e90cbd33257ef443693ee0")
    }

    "generate correct SK from 24 words mnemonic" in {
      val k1 = Eth2.generate("junior silk kind chalk owner present math peace twice cigar diamond rather field amazing better party quantum among pyramid day old inspire skirt mimic")
      k1.get.sk should === (h"2a3b0ee890272cbf80150ff5cdd3954a8735dc654373150319ee611f6be9b860")
    }

    "generate random BLS " in {
      val k1 = Eth2.generateRandom().get
      k1.sk should !== (h"0x0")
      k1.pk should !== (h"0x0")

      info(s"${k1}")
    }

    "sign and verify 1:1" in {
      val s1 = Eth2.sign(bsk1,"message")
      
      info(s"sig=${Util.hex(s1)}")

      Eth2.verify(bpk1,"message",s1) should === (true)
      Eth2.verify(bpk1,"message1",s1) should === (false)
      Eth2.verify(bpk2,"message1",s1) should === (false)
    }

    "msign and mverify 3:3" in {
      val s1 = Eth2.msign(List(bsk1,bsk3,bsk4),"message")
      
      info(s"msig=${Util.hex(s1)}")

      Eth2.mverify(List(bpk1,bpk3,bpk4),"message",s1) should === (true)
      Eth2.mverify(List(bpk4,bpk3,bpk1),"message",s1) should === (true)
      Eth2.mverify(List(bpk3,bpk4,bpk1),"message",s1) should === (true)

      Eth2.mverify(List(bpk1,bpk3,bpk4),"message1",s1) should === (false)
      
      Eth2.mverify(List(bpk1,bpk4),"message",s1) should === (false)
      Eth2.mverify(List(bpk1),"message",s1) should === (false)
      Eth2.mverify(List(bpk3,bpk4),"message",s1) should === (false)
      Eth2.mverify(List(bpk2),"message",s1) should === (false)
      Eth2.mverify(List(),"message",s1) should === (false)
    }
    
    "write and read keystore file with password" in {
      os.remove(os.Path("/tmp/bls-keystore-1.json",os.pwd),false)

      val k1 = Eth2.generate("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat")
      
      val r = Eth2.writeKeystore(k1.get.sk,k1.get.pk,"test1234","/tmp/bls-keystore-1.json")
      r should === (Success("/tmp/bls-keystore-1.json"))
      os.exists(os.Path("/tmp/bls-keystore-1.json",os.pwd)) should === (true)

      val k2 = Eth2.readKeystore("test1234","/tmp/bls-keystore-1.json")
      k2 shouldBe a [Success[_]]
      k2.get.sk should === (h"0x69e0debb3512b0000cabddb5fdce0d10b2b08d03e1e90cbd33257ef443693ee0")
    }

    "write and read keystore file without password" in {
      os.remove(os.Path("/tmp/bls-keystore-2.json",os.pwd),false)

      val k1 = Eth2.generate(bsk1).get
      k1.sk should === (h"0x4130aea3da34b3b5a03305690aaa3bd3a1490e39812f98f0e96b1827929be363")
      
      val r = Eth2.writeKeystore(k1.sk,k1.pk,"","/tmp/bls-keystore-2.json")
      r should === (Success("/tmp/bls-keystore-2.json"))
      os.exists(os.Path("/tmp/bls-keystore-2.json",os.pwd)) should === (true)

      val k2 = Eth2.readKeystore("","/tmp/bls-keystore-2.json")
      k2 shouldBe a [Success[_]]
      info(s"${k2}");
      k2.get.sk should === (h"0x4130aea3da34b3b5a03305690aaa3bd3a1490e39812f98f0e96b1827929be363")
      k2.get.pk should === (h"0x8dfd66bcf262f69c5fd0ebeb266188c1526137091a33c5f0a02b388289ba6afaf92d814396d3260fc6eca05387e46bd7")
    }
    
  }
}
