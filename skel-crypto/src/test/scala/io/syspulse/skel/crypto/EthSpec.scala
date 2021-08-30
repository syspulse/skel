package io.syspulse.skel.crypto

import org.scalatest.{ Matchers, WordSpec }

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util

class EthSpec extends WordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

  val sk1 = "0x00d0f37e94ba4d144291b745212bcb49fff3a6c06f280371faa6dc07640d631ecc"
  val pk1 = "0x6a9218674affe7ffcca2baccc261260e3f2f30166ac1f481d426898236c03d8993b526760c432c643d8be796ff5e3d096152582a4317f3370b8783d2c47274f8"

  "Eth" should {
    "sign and verify signature" in {
      val sig = Eth.sign("message",sk1)
      val v = Eth.verify("message",sig,pk1)
      v should === (true)
    }

    "NOT verify signature for corrupted data" in {
      val sig = Eth.sign("message",sk1)
      val v = Eth.verify("MESSAGE",sig,pk1)
      v should === (false)
    }
  }
}
