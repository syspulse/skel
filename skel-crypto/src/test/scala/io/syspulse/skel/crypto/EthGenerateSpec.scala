package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }

import java.time._
import io.syspulse.skel.util.Util

class EthGenerateSpec extends WordSpec with Matchers {
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
  "Eth" should {
    "always generate 32bytes SK and 64bytes PK" in {
      for( i <- 1 to 1000) {
        val kk = Eth.generate
        kk._1.size should === (32*2+2)
        kk._2.size should === (64*2+2)
      }
    }
  }
}
