package io.syspulse.skel.crypto

import org.scalatest.{ Matchers, WordSpec }

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util

class OpenSSLSpec extends WordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  
  "OpenSSL" should {

    "load PEM file" in {
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      Util.hex(kk.getPublicKey.toByteArray) should === ("0x6a9218674affe7ffcca2baccc261260e3f2f30166ac1f481d426898236c03d8993b526760c432c643d8be796ff5e3d096152582a4317f3370b8783d2c47274f8")
    }

    "recover OpenSSL signature: index 0" in {
      val sig = "733D39E7D03EA340E233D30C67B2B8E857516783D15A4BE2C9720DF4B8CE3F0F:6682259049AD10814BC16552BA813D648C127BAB47DF521B94511BA52CEB1F18"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig.split(":")(0),sig.split(":")(1))
      Util.hex(kk.getPublicKey.toByteArray) should === (pk1._1)
    }

    "recover OpenSSL signature: index 1" in {
      val sig = "F70D78F88257FF1F2FBFDBF8BBD98237156146D2C836361C03276EA83EC48A58:B67216ECCE592B16F1A5374578DCF5E4A533BAE8778A670F377207257F58DFCA"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig.split(":")(0),sig.split(":")(1))
      Util.hex(kk.getPublicKey.toByteArray) should === (pk1._2)
    }

    "recover OpenSSL signature (merged)" in {
      val sig = "733D39E7D03EA340E233D30C67B2B8E857516783D15A4BE2C9720DF4B8CE3F0F:6682259049AD10814BC16552BA813D648C127BAB47DF521B94511BA52CEB1F18"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig)
      Util.hex(kk.getPublicKey.toByteArray) should === (pk1._1)
    }

    "recover OpenSSL signature with blanks" in {
      val sig = " 733D39E7D03EA340E233D30C67B2B8E857516783D15A4BE2C9720DF4B8CE3F0F :  6682259049AD10814BC16552BA813D648C127BAB47DF521B94511BA52CEB1F18  "
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig)
      Util.hex(kk.getPublicKey.toByteArray) should === (pk1._1)
    }

    "verify OpenSSL signature: index 0" in {
      val sig = "733D39E7D03EA340E233D30C67B2B8E857516783D15A4BE2C9720DF4B8CE3F0F:6682259049AD10814BC16552BA813D648C127BAB47DF521B94511BA52CEB1F18"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val v = OpenSSL.verify("message",sig,pk0)
      v should === (true)
    }

    "verify OpenSSL signature: index 1" in {
      val sig = "F70D78F88257FF1F2FBFDBF8BBD98237156146D2C836361C03276EA83EC48A58:B67216ECCE592B16F1A5374578DCF5E4A533BAE8778A670F377207257F58DFCA"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val v = OpenSSL.verify("message",sig,pk0)
      v should === (true)
    }

    "NOT verify OpenSSL signature for corrupted message" in {
      val sig = "F70D78F88257FF1F2FBFDBF8BBD98237156146D2C836361C03276EA83EC48A58:B67216ECCE592B16F1A5374578DCF5E4A533BAE8778A670F377207257F58DFCA"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val v = OpenSSL.verify("MESSAGE",sig,pk0)
      v should === (false)
    }

    "fail to recover invalid OpenSSL signature ''" in {
      val sig = ""
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig)
      pk1._1 should === ("")
    }

    "fail to recover invalid OpenSSL signature ':'" in {
      val sig = ":"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig)
      pk1._1 should === ("")
    }

    "fail to recover invalid OpenSSL signature ':XX'" in {
      val sig = ":6682259049AD10814BC16552BA813D648C127BAB47DF521B94511BA52CEB1F18"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig)
      pk1._1 should === ("")
    }

    "fail to recover invalid OpenSSL signature 'XX:'" in {
      val sig = "733D39E7D03EA340E233D30C67B2B8E857516783D15A4BE2C9720DF4B8CE3F0F:"
      val kk = OpenSSL.loadPem(testDir+"/sk-1.pem")
      val pk0 = Util.hex(kk.getPublicKey.toByteArray)

      val pk1 = OpenSSL.recover("message",sig)
      pk1._1 should === ("")
    }
 
  }
}
