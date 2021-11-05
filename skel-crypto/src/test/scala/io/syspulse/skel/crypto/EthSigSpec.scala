package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }

import java.time._
import io.syspulse.skel.util.Util

class EthSigSpec extends WordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

  // for commented keys, signature is NOT working
  val kk = Seq(
    ("0x000000000000000000000000000000000000000000000000000000000000000001","0x79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8"),
    ("0x000000000000000000000000000000000000000000000000000000000000000002","0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a"),
    ("0x000000000000000000000000000000000000000000000000000000000000000003","0xf9308a019258c31049344f85f89d5229b531c845836f99b08601f113bce036f9388f7b0f632de8140fe337e62a37f3566500a99934c2231b6cb9fd7584b8e672"),
    ("0x00000000000000000000000000000000000000000000000000000000000000ff01","0x63d4523937d9f0960d3ad56140fc484cc4923f2ab6438b9320a96bc437a5fc1c62461c8143417fb81289c9a96cf3fd9b8f695eebbca80a7ab26c717441c05609"), 
    ("0x00000000000000000000000000000000000000000000000000000000000000ff02","0xb7a36287c48ba57cbf33ed6bf630dc84d1196bf00f86b12266587fb55219a68fb85832290299275c26a78aab86a777fa61c589dadcc8b33e4e15d5357f2fc23f"),
    ("0x00000000000000000000000000000000000000000000000000000000000000ff03","0xd8fb72d474f217f38f86369228f3199c3f2ef7db099ff490a58fb79d26c09d2757c564a0def15f95e59206151545ee65bfd30cd679c4d5cbd602ec9226a25a95"),
    ("0x00000000000000000000000000000000000000000000000000000000000000ff04","0x07cc2410c51e825e524461f899f524f02a429537e7dc67b25028fd74f4c34160e1c67d13aa0c8a2d753231f82059768058d7161f01f07709f690f8b4c974cf45"),
    ("0x00000000000000000000000000000000000000000000000000000000000000ff05","0x33eadbb6aef926f7d6f290650cdd8286dac68e94537be20dd17206074e909157d2b4bc3770b66d29270e92bd4d67b1fb3e429fba62a9edd76500209d647f4233"),
  )

  val sk1 = "0x00d0f37e94ba4d144291b745212bcb49fff3a6c06f280371faa6dc07640d631ecc"
  val pk1 = "0x6a9218674affe7ffcca2baccc261260e3f2f30166ac1f481d426898236c03d8993b526760c432c643d8be796ff5e3d096152582a4317f3370b8783d2c47274f8"

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
