package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }

import java.time._
import io.syspulse.skel.util.Util

class EthSpec extends WordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

  val sk1 = "0x00d0f37e94ba4d144291b745212bcb49fff3a6c06f280371faa6dc07640d631ecc"
  val pk1 = "0x6a9218674affe7ffcca2baccc261260e3f2f30166ac1f481d426898236c03d8993b526760c432c643d8be796ff5e3d096152582a4317f3370b8783d2c47274f8"

  "Eth" should {

    "derive address from PK" in {
      val a = Eth.address("0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a")
      a should === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }

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

    "read keystore keystore-1.json" in {
      val kk = Eth.readKeystore("test123",testDir+"/keystore-1.json")
      kk should === (Success("0x02","0xc6047f9441ed7d6d3045406e95c07cd85c778e4b8cef3ca7abac09b95c709ee51ae168fea63dc339a3c58419466ceaeef7f632653266d0e1236431a950cfe52a"))
    }

    "FAIL to read keystore keystore-1.json with invalid password" in {
      val kk = Eth.readKeystore("password",testDir+"/keystore-1.json")
      kk.isFailure should === (true)
    }

    "read mnemonic correctly" in {
      val kk = Eth.readMnemonic("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat")
      kk should === (Success("0x00d1a662526ba15b1147fcd2566ca55f7227451f9a88e83018e8a1948039856a7e","0x306e93a1bd660e6b49de5b6d8522ea2163cb7e8eb96c66f0b13d18d6cc889b3f99f28807536f0e08e392cca56354ef4965343eca2f87ea919339475235ee719e"))
    }

    "read mnemonic correctly with derivation path m/44'/60'/0'/0" in {
      val kk = Eth.readMnemonicDerivation("candy maple cake sugar pudding cream honey rich smooth crumble sweet treat","m/44'/60'/0'/0")
      kk should === (Success("0x00c87509a1c067bbde78beb793e6fa76530b6382a4c0241e5e4a9ec0a0f44dc0d3","0xaf80b90d25145da28c583359beb47b21796b2fe1a23c1511e443e7a64dfdb27d7434c380f0aa4c500e220aa1a9d068514b1ff4d5019e624e7ba1efe82b340a59"))
    }
  }
}
