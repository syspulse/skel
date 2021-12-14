package io.syspulse.skel.crypto.wallet

import scala.util.{Try,Success,Failure}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._

import java.time._
import io.syspulse.skel.util.Util

import io.syspulse.skel.crypto._

class WalletVaultSpec extends AnyWordSpec with Matchers with TestData {
  
  "WalletVaultTest" should {
    
    "sign and verify signature multisig" in {
      val w1 = new WalletVaultTest
      
      val ss = w1.load().get

      ss.size should === (3)
      val sig = w1.msign(sk1,"message".getBytes())
      val v = w1.mverify(sig,pk1,"message".getBytes())
      
      v should === (true)
    }
  }

  "WalletVaultKeyfiles" should {
    
    "read keystores: only keystore-1.json" in {
      val w1 = new WalletVaultKeyfiles(testDir, (keystoreFile) => {"test123"})
      val ss = w1.load().get
      ss.size should === (1)
      ss.toSeq(0)._1 === (UUID("431c4a19-9544-4a12-8cde-824849cb6746"))
      ss.toSeq(0)._2.head.addr === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }

    "read keystores: all jsons" in {
      val w1 = new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
        case "keystore-1.json" => "test123"
        case "keystore-ff04.json" => "abcd1234"
        case "keystore-ff05.json" => "12345678"
        case _ => ""
      }})
      val ss = w1.load().get
      ss.size should === (3)
      ss.toSeq(0)._1 === (UUID("431c4a19-9544-4a12-8cde-824849cb6746"))
      ss.toSeq(0)._2.head.addr === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }
  }

  "WalletVault" should {    
    "load WalletVaultTest + WalletVaultKeyfiles" in {
      WalletVault
        .withWallet(new WalletVaultTest)
        .withWallet(new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
          case "keystore-1.json" => "test123"
          case "keystore-ff04.json" => "abcd1234"
          case "keystore-ff05.json" => "12345678"
          case _ => ""
        }}))
        .load()
      
      WalletVault.size() should === (6)
    
    }
  }


}
