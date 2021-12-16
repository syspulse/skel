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
      val sig = w1.msign("message".getBytes(),Some(sk1))
      
      w1.mverify(sig,"message".getBytes(),Some(pk1)) should === (true)
      w1.mverify(sig,"message1".getBytes(),Some(pk1)) should === (false)
      w1.mverify(sig,"message".getBytes(),Some(pk2)) should === (false)
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
      val w = WalletVault
        .build
        .withWallet(new WalletVaultTest)
        .withWallet(new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
          case "keystore-1.json" => "test123"
          case "keystore-ff04.json" => "abcd1234"
          case "keystore-ff05.json" => "12345678"
          case _ => ""
        }}))
        .load()
      
      w.size() should === (6)
    
    }

    "sign and verfiy with WalletVaultTest + WalletVaultKeyfiles" in {
      val w= WalletVault
        .build
        .withWallet(new WalletVaultTest)
        .withWallet(new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
          case "keystore-1.json" => "test123"
          case "keystore-ff04.json" => "abcd1234"
          case "keystore-ff05.json" => "12345678"
          case _ => ""
        }}))
        .load()
      
      w.size() should === (6)

      val sigs = w.msign("message".getBytes())

      sigs should !== (List())
      sigs.size should === (6)
      info(s"sigs=${sigs}")

      w.mverify(sigs,"message".getBytes()) should === (true)

      w.mverify(sigs,"message1".getBytes()) should === (false)
    }
    
  }

}
