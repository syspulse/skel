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

  "AESS" should {
    
    "encrypt and decrypt 'text'" in {
      val e1 = (new AES).encrypt("text","pass1")
      e1.size !== (0)
      new String(e1) !== ("text")

      val d1 = (new AES).decrypt(e1,"pass1")
      d1 shouldBe a [Success[_]]
      d1 === Success("text")
    }

    "fail to decrypt with wrong password" in {
      val e1 = (new AES).encrypt("text","pass1")
      val d1 = (new AES).decrypt(e1,"pass2")
      d1 shouldBe a [Failure[_]]
    }

    "encrypt and decrypt Base64 'text'" in {
      val e1 = (new AES).encryptBase64("text","pass1")
      e1.size !== (0)
      e1 !== ("text")

      val d1 = (new AES).decryptBase64(e1,"pass1")
      d1 shouldBe a [Success[_]]
      d1 === Success("text")
    }

    "encrypt and decrypt large data" in {
      val data = Random.nextString(65000)
      val e1 = (new AES).encrypt(
        data,
        "pass3"
      )
      e1.size !== (0)
      
      val d1 = (new AES).decrypt(e1,"pass3")
      d1 shouldBe a [Success[_]]
      d1 === Success(data)
    }

  }
}
