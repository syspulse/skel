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
      val sig = w1.msign("message".getBytes(),Some(SK(sk1)))
      
      w1.mverify(sig,"message".getBytes(),Some(PK(pk1))) should === (4)
      w1.mverify(sig,"message1".getBytes(),Some(PK(pk1))) should === (0)
      w1.mverify(sig,"message".getBytes(),Some(PK(pk2))) should === (3)
    }
  }

  "WalletVaultKeyfiles" should {
    
    "read keystores: only keystore-1.json" in {
      val w1 = new WalletVaultKeyfiles(testDir, (keystoreFile) => {Some("test123")})
      val ss = w1.load().get
      ss.size should === (1)
      ss.toSeq(0)._1 === (UUID("431c4a19-9544-4a12-8cde-824849cb6746"))
      ss.toSeq(0)._2.head.addr === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }

    "read keystores: all jsons" in {
      val w1 = new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
        case "keystore-1.json" => Some("test123")
        case "keystore-ff04.json" => Some("abcd1234")
        case "keystore-ff05.json" => Some("12345678")
        case _ => None
      }})
      val ss = w1.load().get
      ss.size should === (3)
      ss.toSeq(0)._1 === (UUID("431c4a19-9544-4a12-8cde-824849cb6746"))
      ss.toSeq(0)._2.head.addr === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }
  }

  "WalletVaultKeyfile" should {
    "load keystore: only keystore-1.json" in {
      val w1 = new WalletVaultKeyfile(testDir + "/" + "keystore-1.json","test123")
      val ss = w1.load().get
      ss.size should === (1)
      ss.toSeq(0)._1 === (UUID("431c4a19-9544-4a12-8cde-824849cb6746"))
      ss.toSeq(0)._2.head.addr === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }

    "load keystores: only with password test123" in {
      val w1 = new WalletVaultKeyfile(testDir,"test123")
      val ss = w1.load().get
      ss.size should === (1)
      ss.toSeq(0)._1 === (UUID("431c4a19-9544-4a12-8cde-824849cb6746"))
      ss.toSeq(0)._2.head.addr === ("0x2b5ad5c4795c026514f8317c7a215e218dccd6cf")
    }
  }

  "WalletVault" should {    
    
    "load all: WalletVaultTest + WalletVaultKeyfiles(*) + WalletVaultKeyfile(keystore-1.json)" in {
      val w = WalletVault
        .build
        .withWallet(new WalletVaultTest)
        .withWallet(new WalletVaultKeyfile(testDir + "/" + "keystore-1.json","test123"))
        .withWallet(new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
          case "keystore-1.json" => Some("test123")
          case "keystore-ff04.json" => Some("abcd1234")
          case "keystore-ff05.json" => Some("12345678")
          case _ => None
        }}))
        .load()
      
      w.size() should === (7)
    }

    "msign and mverfiy with WalletVaultTest + WalletVaultKeyfiles" in {
      val w= WalletVault
        .build
        .withWallet(new WalletVaultTest)
        .withWallet(new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
          case "keystore-1.json" => Some("test123")
          case "keystore-ff04.json" => Some("abcd1234")
          case "keystore-ff05.json" => Some("12345678")
          case _ => None
        }}))
        .load()
      
      w.size() should === (6)

      val sigs = w.msign("message".getBytes())

      sigs should !== (List())
      sigs.size should === (6)
      
      w.mverify(sigs,"message".getBytes()) should === (true)

      w.mverify(sigs,"message1".getBytes()) should === (false)
    }
    
    "msign and mverfiy 3:6" in {
      val w6= WalletVault
        .build
        .withWallet(new WalletVaultTest)
        .withWallet(new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
          case "keystore-1.json" => Some("test123")
          case "keystore-ff04.json" => Some("abcd1234")
          case "keystore-ff05.json" => Some("12345678")
          case _ => None
        }}))
        .load()

      val w3= WalletVault
        .build
        .withWallet(new WalletVaultTest)
        .load()
      
      w6.size() should === (6)
      w3.size() should === (3)

      val sigs3 = w3.msign("message".getBytes())

      sigs3 should !== (List())
      sigs3.size should === (3)
      // info(s"sigs (3)=${sigs3}")

      w6.mverify(sigs3,"message".getBytes(), m = 3) should === (true)
      w6.mverify(sigs3,"message1".getBytes(), m = 3) should === (false)  

      // shuffled
      val w61= WalletVault
        .build
        .withWallet(new WalletVaultKeyfiles(testDir, (keystoreFile) => { keystoreFile match {
          case "keystore-1.json" => Some("test123")
          case "keystore-ff04.json" => Some("abcd1234")
          case "keystore-ff05.json" => Some("12345678")
          case _ => None
        }}))
        .withWallet(new WalletVaultTest().shuffle())
        .load()

      w61.mverify(sigs3,"message".getBytes(), m = 3) should === (true)
      w61.mverify(sigs3,"message1".getBytes(), m = 3) should === (false)  

    }

  }

}
