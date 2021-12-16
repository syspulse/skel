package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class Eth2Spec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "Eth2" should {
    
    "generate SK from 12 words mnemonic" in {
      val k1 = Eth2.generate("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat")
      k1.get.sk should === (h"0x69e0debb3512b0000cabddb5fdce0d10b2b08d03e1e90cbd33257ef443693ee0")
    }

    "generate correct SK from 24 words mnemonic" in {
      val k1 = Eth2.generate("junior silk kind chalk owner present math peace twice cigar diamond rather field amazing better party quantum among pyramid day old inspire skirt mimic")
      k1.get.sk should === (h"2a3b0ee890272cbf80150ff5cdd3954a8735dc654373150319ee611f6be9b860")
    }
    
  }
}
