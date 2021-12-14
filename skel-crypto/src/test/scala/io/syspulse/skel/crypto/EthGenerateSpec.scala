package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthGenerateSpec extends AnyWordSpec with Matchers  {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

// sbt and web3j bouncycastle provider don't work together in tests
// When test is run second time it generate this exception:
//
// java.lang.ClassCastException: class org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey cannot be cast to class org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey 
// (org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey is in unnamed module of loader sbt.internal.LayeredClassLoader @7d9f790b; org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPriva
// teKey is in unnamed module of loader sbt.internal.LayeredClassLoader @32bb6767)
// [info]   at org.web3j.crypto.ECKeyPair.create(ECKeyPair.java:63)
// [info]   at org.web3j.crypto.Keys.createEcKeyPair(Keys.java:89)
// [info]   at org.web3j.crypto.Keys.createEcKeyPair(Keys.java:82)
  "EthGenerate" should {
    "always generate 32bytes SK and 64bytes PK" ignore {
      for( i <- 1 to 1000) {
        val kk = Eth.generate
        kk._1.size should === (32*2+2)
        kk._2.size should === (64*2+2)
      }
    }

    "generate for SK=0x1: PK=0x79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8" in {
      val kk = Eth.generate("0x01")
      info(s"${kk}")
      kk._1.size should === (32*2+2)
      kk._2.size should === (64*2+2)
      kk._2 should === ("0x79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8")
    }

    "generate for SK=0x2: PK=0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a" in {
      val kk = Eth.generate("0x02")
      info(s"${kk}")
      kk._1.size should === (32*2+2)
      kk._2.size should === (64*2+2)
      kk._2 should === ("0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a")
    }

    "generate for SK=0x3: PK=0xf9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9388f7b0f632de8140fe337e62a37f3566500a99934c2231b6cb9fd7584b8e672" in {
      val kk = Eth.generate("0x03")
      info(s"${kk}")
      kk._1.size should === (32*2+2)
      kk._2.size should === (64*2+2)
      kk._2 should === ("0xf9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9388f7b0f632de8140fe337e62a37f3566500a99934c2231b6cb9fd7584b8e672")
    }

    "generate test keys:" in {
      for( i <- 0xff01 to 0xff05) {
        val kk = Eth.generate(Util.hex(BigInt(i).toByteArray))
        info(s"${kk}")
        kk._1.size should === (32*2+2)
        kk._2.size should === (64*2+2)
      }
    }
  }
}
