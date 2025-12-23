package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class KeySpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "Key" should {
    
    "Key equality (Scalatest cannot perform embedded Array equality)" in {
      val k1 = KeyBLS(h"0x69e0debb3512b0000cabddb5fdce0d10b2b08d03e1e90cbd33257ef443693ee0",h"01")
      val k2 = KeyBLS(h"0x69e0debb3512b0000cabddb5fdce0d10b2b08d03e1e90cbd33257ef443693ee0",h"02")
      k1.sk should === (k2.sk)
      k1.sk should !== (k2.pk)
      k1.pk should !== (k2.pk)
    }    
  }
}
