package io.syspulse.skel.crypto.kms

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

    val keyId = sys.env.get("AWS_KMS_KEY").getOrElse("arn:UNKNOWN-KEY")
    val keyId2 = "arn:aws:kms:eu-west-1:111111111111:key/1594b719-4d64-4581-b682-8bd4a94d2a30"
    
    "get ARN by alias" in {      
      val kid = (new AES).getKeyId("extractor/wallet")
      kid shouldBe a [Success[_]]
      kid === Success(keyId)
    }

    // "encrypt and decrypt 'text'" in {
    //   val e1 = (new AES).encrypt("text",keyId)
    //   e1.size !== (0)
    //   new String(e1) !== ("text")

    //   val d1 = (new AES).decrypt(e1,keyId)
    //   d1 shouldBe a [Success[_]]
    //   d1 === Success("text")
    // }

    // "fail to decrypt with wrong keyId" in {
    //   val e1 = (new AES).encrypt("text",keyId)
    //   val d1 = (new AES).decrypt(e1,keyId2)
    //   d1 shouldBe a [Failure[_]]
    // }

    // "encrypt and decrypt Base64 'text'" in {
    //   val e1 = (new AES).encryptBase64("text",keyId)
    //   e1.size !== (0)
    //   e1 !== ("text")

    //   val d1 = (new AES).decryptBase64(e1,keyId)
    //   d1 shouldBe a [Success[_]]
    //   d1 === Success("text")
    // }

    // "encrypt and decrypt data block (128K)" in {
    //   val data = Random.nextString(1024 * 128)
    //   val e1 = (new AES).encrypt(
    //     data,
    //     keyId
    //   )
    //   e1.size !== (0)
      
    //   val d1 = (new AES).decrypt(e1,keyId)
    //   d1 shouldBe a [Success[_]]
    //   d1 === Success(data)
    // }

  }
}
