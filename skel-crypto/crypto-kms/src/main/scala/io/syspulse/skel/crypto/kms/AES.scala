package io.syspulse.skel.crypto.kms

import scala.util.{Try,Success,Failure}
import os._

import scala.jdk.CollectionConverters._
import java.math.BigInteger

import java.security.Security

import io.syspulse.skel.util.Util
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.spec.KeySpec
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec
import java.security.SecureRandom
import javax.crypto.Cipher
import java.io.InputStream
import java.io.OutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Base64

import java.nio.charset.StandardCharsets
import com.amazonaws.services.kms.{AWSKMS, AWSKMSClientBuilder}
import com.amazonaws.services.kms.model.DecryptRequest
import com.amazonaws.services.kms.model.EncryptRequest
import com.amazonaws.services.kms.model.GenerateDataKeyRequest
import java.nio.ByteBuffer
import com.amazonaws.services.kms.model.DescribeKeyRequest
import com.amazonaws.services.kms.model.ListAliasesRequest

class AES {
  import Util._
  val kms: AWSKMS = AWSKMSClientBuilder.standard.build

  def decrypt(input: Array[Byte], keyId:String): Try[String] = {
    try {      
      if(input.size > 4096) {
        
          // derive the datakey (256 bits)
          val rawKey = input.take(32)
          val rawData = input.drop(32)
          
          new io.syspulse.skel.crypto.AES().decrypt(rawData,new String(rawKey))

      } else {
        val data: ByteBuffer = ByteBuffer.wrap(input)

        val req: DecryptRequest = new DecryptRequest().withCiphertextBlob(data).withKeyId(keyId)
        val raw: ByteBuffer = kms.decrypt(req).getPlaintext

        val output = StandardCharsets.UTF_8.decode(raw).toString
        Success(output)      
      }
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def encrypt(input: String, keyId:String): Array[Byte] = {

    if(input.size > 2048) {
        val req: GenerateDataKeyRequest = new GenerateDataKeyRequest().withKeyId(keyId).withKeySpec("AES_256")
        val dataKey = kms.generateDataKey(req)
        // val reqKey: EncryptRequest = new EncryptRequest().withPlaintext(dataKey.getPlaintext()).withKeyId(keyId)
        // val rawKey: ByteBuffer = kms.encrypt(reqKey).getCiphertextBlob()
        val rawKey = dataKey.getPlaintext()
        
        val rawData = new io.syspulse.skel.crypto.AES().encrypt(input,new String(rawKey.array()))
        rawKey.array() ++ rawData 

    } else {
        val data: ByteBuffer = ByteBuffer.wrap(input.getBytes())
        val req: EncryptRequest = new EncryptRequest().withPlaintext(data).withKeyId(keyId)
        val raw: ByteBuffer = kms.encrypt(req).getCiphertextBlob()
        raw.array()
    }  
  }    

  def encryptBase64(input:String, keyId:String):String = {
    Base64.getEncoder().encodeToString(
      encrypt(input,keyId)
    )
  }
  
  def decryptBase64(input:String, keyId:String):Try[String] = {
    decrypt(
      Base64.getDecoder().decode(input),
      keyId
    )
  }
  
  def getKeyId(alias:String):Try[String] = {    
    try {
      val req = new ListAliasesRequest().withLimit(512)
      val aa = kms.listAliases(req)
      aa.getAliases().asScala.find(_.getAliasName() == s"alias/${alias}") match {
        case Some(k) => Success(k.getAliasName())
        case _ => Failure(new Exception(s"keyId not found: ${alias}"))
      }      
    } catch {
      case e:Exception => Failure(e)
    }
  }

}

