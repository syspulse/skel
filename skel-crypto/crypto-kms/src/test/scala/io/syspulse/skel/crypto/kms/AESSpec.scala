package io.syspulse.skel.crypto.kms

import scala.util.{Try,Success,Failure}

import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.syspulse.skel.util.Util
import scala.util.Random
import com.amazonaws.services.kms.model.CustomerMasterKeySpec
import com.amazonaws.services.kms.model.CreateKeyRequest
import com.amazonaws.services.kms.AWSKMS

class AESSpec extends AnyWordSpec with Matchers with TestData {
  import Util._

  "AESS" should {

    val keyId = sys.env.get("AWS_KMS_KEY").getOrElse("arn:UNKNOWN-KEY")
    val keyId2 = "arn:aws:kms:eu-west-1:111111111111:key/1594b719-4d64-4581-b682-8bd4a94d2a30"
    
    "get ARN by alias" in {
      val a1 = new AES("http://localhost:4599")
      val keyId = a1.getKmsClient().createKeyAES("key0",Some("service/key"))

      val kid = (new AES("http://localhost:4599")).getKeyId("service/key",100)
      kid shouldBe a [Success[_]]
      kid should === (Success(keyId))
    }

    "encrypt and decrypt 'text'" in {
      val a1 = new AES("http://localhost:4599")
      val keyId = a1.getKmsClient().createKeyAES("key1")
      val e1 = a1.encrypt("text",keyId)
      
      e1.size should !== (0)
      new String(e1) should !== ("text")

      val d1 = (new AES("http://localhost:4599")).decrypt(e1,keyId)
      d1 shouldBe a [Success[_]]
      d1 should === (Success("text"))
    }

    "fail to decrypt with wrong keyId" in {
      val a1 = new AES("http://localhost:4599")
      val keyId = a1.getKmsClient().createKeyAES("key2")
      val keyId3 = a1.getKmsClient().createKeyAES("key3")
      val keyId4 = "arn:aws:kms:eu-west-1:111111111111:key/1594b719-4d64-4581-b682-8bd4a94d2a30"
      
      val e1 = a1.encrypt("text",keyId)
      
      val d3 = a1.decrypt(e1,keyId3)
      d3 shouldBe a [Failure[_]]

      val d4 = a1.decrypt(e1,keyId4)
      d4 shouldBe a [Failure[_]]
    }

    "encrypt and decrypt Base64 'text'" in {
      val a1 = new AES("http://localhost:4599")
      val keyId = a1.getKmsClient().createKeyAES("key5")

      val e1 = a1.encryptBase64("text",keyId)
      e1.size should !== (0)
      e1 should !== ("text")

      val d1 = a1.decryptBase64(e1,keyId)
      d1 shouldBe a [Success[_]]
      d1 should === (Success("text"))
    }

    "encrypt and decrypt data block (128K)" in {
      val a1 = new AES("http://localhost:4599")
      val keyId = a1.getKmsClient().createKeyAES("key6")

      val data = Random.nextString(1024 * 128)
      val e1 = a1.encrypt(
        data,
        keyId
      )
      e1.size should !== (0)
      
      val d1 = a1.decrypt(e1,keyId)      

      d1 shouldBe a [Success[_]]
      d1 should === (Success(data))
    }

    "encrypt and decrypt data block (10M)" in {
      val a1 = new AES("http://localhost:4599")
      val keyId = a1.getKmsClient().createKeyAES("key7")

      val data = Random.nextString(1024 * 1024 * 10)
      val e1 = a1.encrypt(
        data,
        keyId
      )
      e1.size should !== (0)
      
      val d1 = a1.decrypt(e1,keyId)      

      d1 shouldBe a [Success[_]]
      d1 should === (Success(data))
    }

  }
}
