package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util

class EthSpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "Eth" should {
    
    "derive address from PK" in {
      val a = Eth.address("0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a")
      a should === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }

    "generate random" ignore {
      Range(1,3).map(i => 
          Eth.generateRandom()
        )
        .foreach{ case (sk,pk) => {
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
      val kk = Eth.readKeystore("test123",testDir+"/keystore-1.json").map(k => (Util.hex(k._1),Util.hex(k._2)))
      kk should === (Success("0x02","0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a"))
    }

    "FAIL to read keystore keystore-1.json with invalid password" in {
      val kk = Eth.readKeystore("password",testDir+"/keystore-1.json")
      kk.isFailure should === (true)
    }

    "read mnemonic correctly" in {
      val kk = Eth.generateFromMnemo("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat").map(k => (Util.hex(k._1),Util.hex(k._2)))
      kk should === (Success(("0x00d1a662526ba15b1147fcd2566ca55f7227451f9a88e83018e8a1948039856a7e","0x306e93a1bd660e6b49de5b6d8522ea2163cb7e8eb96c66f0b13d18d6cc889b3f99f28807536f0e08e392cca56354ef4965343eca2f87ea919339475235ee719e")))
    }

    "read mnemonic correctly with derivation path m/44'/60'/0'/0" in {
      val kk = Eth.generateFromMnemoPath("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat","m/44'/60'/0'/0").map(k => (Util.hex(k._1),Util.hex(k._2)))
      kk should === (Success("0x00c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3","0xaf80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d7434c380f0aa4c500e220aa1a9d068514b1ff4d5019e624e7ba1efe82b340a59"))
    }
  }
}
