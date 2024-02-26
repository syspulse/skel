package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util
import scala.util.Random

class AESSpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "AES" should {
    
    "encrypt and decrypt 'text'" in {
      val e1 = (new AES).encrypt("text","pass1")
      e1.size !== (0)
      new String(e1) !== ("text")

      val d1 = (new AES).decrypt(e1,"pass1")
      d1 shouldBe a [Success[_]]
      d1 should === (Success("text"))
    }

    "encrypt and decrypt 'text' with random seed" in {
      val seed = Random.nextString(16)
      val e1 = (new AES).encrypt("text","pass1",Some(seed))
      e1.size !== (0)
      new String(e1) !== ("text")

      val d1 = (new AES).decrypt(e1,"pass1",Some(seed))
      d1 shouldBe a [Success[_]]
      d1 should === (Success("text"))

      val d2 = (new AES).decrypt(e1,"pass1")
      d2 shouldBe a [Failure[_]]
    }

    "fail to decrypt with wrong password" in {
      val e1 = (new AES).encrypt("text","pass1")
      val d1 = (new AES).decrypt(e1,"pass2")
      d1 shouldBe a [Failure[_]]
    }

    "fail to decrypt with wrong IV and correct pass" in {
      val e1 = (new AES).encrypt("text","pass1",Some("seed-1"))
      val d1 = (new AES).decrypt(e1,"pass1")
      d1 shouldBe a [Failure[_]]
    }

    "fail to decrypt with random IV and correct pass" in {
      val e1 = (new AES).encrypt("text","pass1",None)
      val d1 = (new AES).decrypt(e1,"pass1")
      d1 shouldBe a [Failure[_]]
    }

    "fail to decrypt with different IVs and correct pass" in {
      val e1 = (new AES).encrypt("text","pass1",Some("seed-1"))
      val d1 = (new AES).decrypt(e1,"pass1",Some("seed-2"))
      d1 shouldBe a [Failure[_]]
    }

    "fail to decrypt with random IV encryption and random IV decryption and  correct pass" in {
      val e1 = (new AES).encrypt("text","pass1",None)
      val d1 = (new AES).decrypt(e1,"pass1",None)
      d1 shouldBe a [Failure[_]]
    }

    "encrypt and decrypt Base64 'text'" in {
      val e1 = (new AES).encryptBase64("text","pass1")
      e1.size should !== (0)
      e1 should !== ("text")

      val d1 = (new AES).decryptBase64(e1,"pass1")
      d1 shouldBe a [Success[_]]
      d1 should === (Success("text"))
    }

    "encrypt and decrypt data block (128K)" in {
      val data = Random.nextString(1024 * 128)
      val e1 = (new AES).encrypt(
        data,
        "pass3"
      )
      e1.size should !== (0)
      
      val d1 = (new AES).decrypt(e1,"pass3")
      d1 shouldBe a [Success[_]]
      d1 should === (Success(data))
    }

  }
}
