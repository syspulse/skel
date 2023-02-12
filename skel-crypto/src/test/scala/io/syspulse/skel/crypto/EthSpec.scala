package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash

class EthSpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "Eth" should {
    
    "derive address from PK" in {
      val a = Eth.address("0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a")
      a should === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }

    "generate random" ignore {
      Range(1,3).map(i => 
          Eth.generateRandom().get
        )
        .foreach{ case KeyECDSA(sk,pk) => {
          val a = Eth.address(pk)
          info(s"sk = ${sk}, pk = ${pk}, a = ${a}")
          sk should !== (null)
          sk should !== ("")
        }}
    }

    "derive new SecretKeys from SK" in {
      val sk2 = Eth.deriveKey(sk1,"Account Key 1")
      val sk3 = Eth.deriveKey(sk1,"Account Key 1")
      val sk4 = Eth.deriveKey(sk1,"Account Key 2")
      sk2 should !== (sk1)
      sk3 should !== (sk1)
      sk4 should !== (sk1)

      sk2 should === (sk3)
      sk4 should !== (sk2)
      sk4 should !== (sk3)
    }
    

    "read keystore keystore-1.json" in {
      val kk = Eth.readKeystore("test123",testDir+"/keystore-1.json").map(kp => (Util.hex(kp.sk),Util.hex(kp.pk)))
      kk should === (Success("0x02","0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a"))
    }

    "FAIL to read keystore keystore-1.json with invalid password" in {
      val kk = Eth.readKeystore("password",testDir+"/keystore-1.json")
      kk.isFailure should === (true)
    }

    "read mnemonic correctly" in {
      val kk = Eth.generateFromMnemo("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat").map(kp => (Util.hex(kp.sk),Util.hex(kp.pk)))
      kk should === (Success(("0x00d1a662526ba15b1147fcd2566ca55f7227451f9a88e83018e8a1948039856a7e","0x306e93a1bd660e6b49de5b6d8522ea2163cb7e8eb96c66f0b13d18d6cc889b3f99f28807536f0e08e392cca56354ef4965343eca2f87ea919339475235ee719e")))
      val addr = Eth.address(Util.fromHexString(kk.get._2))
      addr should === ("0x3c5f859ee194b293871cf01a6eb072edd2e46a22")
    }

    // Generally I would recommend that if you are storing it, that you actually use the compressed format, 
    // which begins with 0x02 or 0x03 and is a total of 33 bytes long. You can use the computePublicKey(publicKey, true) 
    // to get the compressed key. The key can then be uncompressed using computePublicKey(publicKey). 
    // This works because a public key is just a point on the elliptic curve, which has two y values for any given x (more of less), 
    // so the compressed key is just the x coordinate and the 0x02 or 0x03 prefix specify whether to take the positive or negative y.
    "match ethers mnemonic (Metamask)" in {
      val kk = Eth.generateFromMnemoPath("announce room limb pattern dry unit scale effort smooth jazz weasel alcohol","m/44'/60'/0'/0").map(kp => (Util.hex(kp.sk),Util.hex(kp.pk)))
      
      // leading 0x04 !
      val ethersPrivateKey = h"0x1da6847600b0ee25e9ad9a52abbd786dd2502fa4005dd5af9310b7cc7a3b25db"
      val ethersPublicKey = h"0x04b9e72dfd423bcf95b3801ac93f4392be5ff22143f9980eb78b3a860c4843bfd04829ae61cdba4b3b1978ac5fc64f5cc2f4350e35a108a9c9a92a81200a60cd64"
      val addr = Eth.address(ethersPublicKey)
      
      kk should === (Success((
        Util.hex(ethersPrivateKey),
        Util.hex(ethersPublicKey.drop(1)))))

      info(s"addr=${addr}")
    }

    "read mnemonic correctly with Metamask derivation path m/44'/60'/0'/0" in {
      val kk = Eth.generateFromMnemoPath("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat","m/44'/60'/0'/0").map(kp => (Util.hex(kp.sk),Util.hex(kp.pk)))
      kk should === (Success("0x00c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3","0xaf80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d7434c380f0aa4c500e220aa1a9d068514b1ff4d5019e624e7ba1efe82b340a59"))
      val addr = Eth.address(Util.fromHexString(kk.get._2))
      //info(s"addr=${addr}")
      addr should === ("0x627306090abaB3A6e1400e9345bC60c78a8BEf57".toLowerCase())
    }
    
  }
}
