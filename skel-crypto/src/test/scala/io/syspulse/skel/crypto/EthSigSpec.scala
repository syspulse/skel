package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }

import java.time._
import io.syspulse.skel.util.Util

class EthSigSpec extends WordSpec with Matchers with TestData {

  "EthSigSpec" should {
    // "sign and verify large keys signatures" in {
    //   val sig = Eth.sign("message","0x4835058d139dbfd9890b152946f466c4bd5f8ae4713d5f12bfc32acce3c4b66d")
    //   //val v = Eth.verify("message",sig,"0x009b796dfdafc2e469f0ed88b1c210c1185472d7d0a85945fc4f223d5c7ec017cb30550dc73ae16b011ddb358b9d14699c36a939dc198bf077a89535032b6eddb4")
    //   val v = Eth.verify("message",sig,"0x9b796dfdafc2e469f0ed88b1c210c1185472d7d0a85945fc4f223d5c7ec017cb30550dc73ae16b011ddb358b9d14699c36a939dc198bf077a89535032b6eddb4")
    //   v should === (true)
    // }

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

    "NOT verify empty signature" in {
      val v = Eth.verify("MESSAGE","",pk1)
      v should === (false)
    }

    "NOT verify invalid signature format" in {
      val v = Eth.verify("MESSAGE","123",pk1)
      v should === (false)
    }

    "NOT verify invalid signature" in {
      val v = Eth.verify("MESSAGE","0x1",pk1)
      v should === (false)
    }

    "sign and verify random keys signatures" in {
      Range(1,100).map(i => 
          Eth.generate(
            Util.hex(BigInt(Util.generateRandom()).toByteArray)
          )
        )
        .foreach( k => {
          val sig = Eth.sign("message",k._1)
          val v = Eth.verify("message",sig,k._2)
          //info(s"sk=${k._1}, pk=${k._2}")
          v should === (true)
        })
    }

    "sign and verify signature small keys signatures" in {
      val sig = Eth.sign("message",kk(1)._1)
      val v = Eth.verify("message",sig,kk(1)._2)
      v should === (true)
    }
  }

}
