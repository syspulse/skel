package io.syspulse.skel.crypto

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

object AES {
  val DEFAULT_ALGO = "AES/CBC/PKCS5Padding"
  val DEFAULT_PBKDF = "PBKDF2WithHmacSHA256"
}

class AES {
  import Util._

  def generateIv(seed:Option[String] = Some("")):IvParameterSpec = {
    val iv = seed match {
      case Some(seed) => new IvParameterSpec(SHA256(seed).take(16))
      case None => generateIvRandom()
    }
    iv
  }

  def generateIvRandom():IvParameterSpec = {
    val iv = generateRandom()
    new IvParameterSpec(iv)
  }

  def generateSeedRandom():String = {
    Util.hex(generateRandom())
  }

  def generateRandom(size:Int = 16):Array[Byte] = {
    val block = Array.ofDim[Byte](size) //Array.fill[Byte](size){0}
    val sr = new SecureRandom()
    sr.nextBytes(block)
    block
  }

  def getKeyFromPassword(password:String,salt:String = "salt"):SecretKey = {
    val factory = SecretKeyFactory.getInstance(AES.DEFAULT_PBKDF);
    //val spec:KeySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 65536, 256);
    val spec:KeySpec = new PBEKeySpec(password.toCharArray(), salt.getBytes(), 1, 256);
    val secretKey = new SecretKeySpec(factory.generateSecret(spec).getEncoded(), "AES");
    secretKey
  }

  def getKey(secret:Array[Byte]):SecretKey = {
    new SecretKeySpec(secret, "AES")
  }
  
  def encrypt(input:String,password:String,seed:Option[String]=None,algo:String=AES.DEFAULT_ALGO):(Array[Byte],Array[Byte]) = {
    val iv:IvParameterSpec = generateIv(seed)
    val secretKey = getKeyFromPassword(password)
    val cipher = Cipher.getInstance(algo)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv)
    val encryptedData = cipher.doFinal(input.getBytes)
    (iv.getIV(),encryptedData)
  }

  def encryptBase64(input:String,password:String,seed:Option[String]=None,algo:String=AES.DEFAULT_ALGO):(String,String) = {
    val (iv,data) = encrypt(input,password,seed,algo)
    (
      Base64.getEncoder().encodeToString(iv),
      Base64.getEncoder().encodeToString(data)
    )
  }
  

  def decryptBytes(input:Array[Byte],password:String,ivData:Array[Byte],algo:String=AES.DEFAULT_ALGO):Try[Array[Byte]] = {
    val iv:IvParameterSpec = new IvParameterSpec(ivData) //generateIv(seed)
    val secretKey = getKeyFromPassword(password)
    val cipher = Cipher.getInstance(algo)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
    try {
      val decryptedData = cipher.doFinal(input);
      Success(decryptedData)
    } catch {
      case e:Exception => Failure(e)
    }
  }

  def decrypt(input:Array[Byte],password:String,ivData:Array[Byte],algo:String=AES.DEFAULT_ALGO):Try[String] = {
    decryptBytes(input,password,ivData,algo).map(o => new String(o))
  }

  def decryptBase64(input:String,password:String,ivData:String,algo:String=AES.DEFAULT_ALGO):Try[String] = {
    decrypt(Base64.getDecoder().decode(input),password,Base64.getDecoder().decode(ivData),algo)
  }

  // returns IV
  def encryptStream(in:InputStream,out:OutputStream,password:String,seed:Option[String]=None,algo:String=AES.DEFAULT_ALGO):Array[Byte] = {
    val iv:IvParameterSpec = generateIv(seed)
    val secretKey = getKeyFromPassword(password)
    val cipher = Cipher.getInstance(algo)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, iv);
    
    val buffer = new Array[Byte](64)

    var bytesRead:Int = 0
    while ({ bytesRead = in.read(buffer); bytesRead } != -1) {
      var output = cipher.update(buffer, 0, bytesRead)
      if (output != null) {
        out.write(output)
      }
    }
    val outputBytes = cipher.doFinal()
    if (outputBytes != null) {
      out.write(outputBytes)
    }
    
    iv.getIV()
  }

  def encryptFile(inFile:String,outFile:String,password:String,seed:Option[String]=Some(""),algo:String=AES.DEFAULT_ALGO):Array[Byte] = {
    val in:FileInputStream = new FileInputStream(inFile)
    val out:FileOutputStream = new FileOutputStream(outFile)
    
    val iv = encryptStream(in,out,password,seed,algo)

    in.close()
    out.close()
    iv
  }

}


