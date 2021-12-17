package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import scala.jdk.CollectionConverters._

import at.favre.lib.crypto._

import java.nio.charset.StandardCharsets
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;
import java.security.MessageDigest 

// BLS
import tech.pegasys.teku.bls._
import org.apache.tuweni.bytes._

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.key._

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

  def generate(sk:SK):KeyPair = {
    val k = BLSKeyPair.random(Util.random)
    KeyBLS(k.getSecretKey.toBytes().toArray(),k.getPublicKey.toBytesCompressed().toArray())
  }

  def generateRandom():KeyPair = {
    val k = BLSKeyPair.random(Util.random)
    KeyBLS(k.getSecretKey.toBytes().toArray(),k.getPublicKey.toBytesCompressed().toArray())
  }

  def sign(sk:SK,m:String):Array[Byte] =  sign(sk,m.getBytes)

  def sign(sk:SK,m:Array[Byte]):Array[Byte] = {
    val blsSk = BLSSecretKey.fromBytes(Bytes32.wrap(sk))
    val hash = Util.SHA256(m)
    val sig = BLS.sign(blsSk,Bytes.of(hash:_*))
    sig.toBytesCompressed().toArray()
  }

  def verify(pk:PK,m:String,sig:Array[Byte]):Boolean = verify(pk,m.getBytes(),sig)
  def verify(pk:PK,m:Array[Byte],sig:Array[Byte]):Boolean = {
    val blsPk = BLSPublicKey.fromBytesCompressed(Bytes48.wrap(pk))
    val hash = Util.SHA256(m)
    BLS.verify(blsPk,Bytes.of(hash:_*),BLSSignature.fromBytesCompressed(Bytes.of(sig:_*)))
  }

  def msign(sk:List[SK],m:String):Array[Byte] = msign(sk,m.getBytes())
  def msign(sk:List[SK],m:Array[Byte]):Array[Byte] = {
    val sigs = sk.map( sk => {
      val blsSk = BLSSecretKey.fromBytes(Bytes32.wrap(sk))
      val hash = Util.SHA256(m)
      BLS.sign(blsSk,Bytes.of(hash:_*))
    }) 
    BLS.aggregate(sigs.asJava).toBytesCompressed().toArray()
  }

  def mverify(pk:List[PK],m:String,sig:Array[Byte]):Boolean = mverify(pk,m.getBytes,sig)
  def mverify(pk:List[PK],m:Array[Byte],sig:Array[Byte]):Boolean = {
    val blsPk = pk.map( pk => {
      BLSPublicKey.fromBytesCompressed(Bytes48.wrap(pk))
    }) 
    val hash = Util.SHA256(m)
    BLS.fastAggregateVerify(blsPk.asJava,Bytes.of(hash:_*),BLSSignature.fromBytesCompressed(Bytes.of(sig:_*)))
  }

}
