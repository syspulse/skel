package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import scala.jdk.CollectionConverters

import at.favre.lib.crypto._
//import scodec.bits._
import java.nio.charset.StandardCharsets
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;
import java.security.MessageDigest 

import io.syspulse.skel.util.Util

object Eth2 {
  
  def generate(mnemonic:String):Try[KeyBLS] = { 
    // mnemonic password is not used
    val mnemonicPassword = ""

    // hardcoded in eth2deposit
    val saltSmnemonic = "mnemonic" + mnemonicPassword

    def b2d(b:Array[Byte])=b.reverse.zipWithIndex.foldLeft(BigInt(0))((v,z) => v+(0xff & z._1)*BigInt(2).pow(z._2*8))
    def d2b(b:BigInt):Array[Byte] = if(b > 0) { val v = (b /% 256)._2.toByte; d2b((b/%256)._1) :+ v} else Array()
    def pbkdf2(password:String, salt:Array[Byte], iterations:Int, size:Int):Array[Byte] = {
        val spec = new PBEKeySpec(password.toCharArray, salt, iterations, size * 8);
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        skf.generateSecret(spec).getEncoded()
    }

    // get the seed 
    val seed = pbkdf2(mnemonic,saltSmnemonic.getBytes,2048,64)
    
    val sha = MessageDigest.getInstance("SHA-256")
    val salt_hkdf = "BLS-SIG-KEYGEN-SALT-"
    val salt_hkdf_sha256 = sha.digest(salt_hkdf.getBytes(StandardCharsets.UTF_8))

    val hkdf = HKDF.fromHmacSha256()
    val seed_IKM = seed :+ 0x0.toByte
    val L = 48
    // info == 0030
    val info = Array[Byte](0,L.toByte)
    val okm = HKDF.fromHmacSha256().extractAndExpand(salt_hkdf_sha256, seed_IKM, info, L )

    // BLS curve
    val bls_curve_order = BigInt("52435875175126190479447740508185965837690552500527637822603658699938581184513")

    val sk = d2b(b2d(okm) % bls_curve_order)
    Success(KeyBLS(sk,Array[Byte]()))
  }  
}
