package io.syspulse.skel.crypto

import collection.JavaConverters._

import org.web3j.crypto._
import org.web3j.utils._
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.io.pem.PemReader

import java.security.KeyFactory
import java.security.Security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.ECGenParameterSpec

import java.math.BigInteger
import java.io.StringReader
import java.io.FileReader
import scala.io.Source

import io.syspulse.skel.util.Util
import io.syspulse.skel.crypto.Hash

object OpenSSL {
  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  def loadPem(pemFile:String):ECKeyPair = {
    val p = new PemReader(new StringReader(Source.fromFile(pemFile).getLines.mkString("\n")))
    val spec = new PKCS8EncodedKeySpec(p.readPemObject.getContent)
    val skHex = "0x"+Util.hex(spec.getEncoded).substring(68,68+64) 
    ECKeyPair.create(Numeric.hexStringToByteArray(skHex))
  }

  def recover(m:String,r:String,s:String):(String,String) = {     
    if(m==null|| r==null || s==null || r.trim.isEmpty() || s.trim.isEmpty()) return ("","")
    val sig = new ECDSASignature(new BigInteger(Numeric.hexStringToByteArray("0x00"+r.trim)),new BigInteger(Numeric.hexStringToByteArray("0x00"+s.trim))) 
    val h = Hash.keccak256(m)
    (
      Util.hex(Sign.recoverFromSignature(0,sig,h).toByteArray),
      Util.hex(Sign.recoverFromSignature(1,sig,h).toByteArray)
    )
  }

  def recover(m:String,rs:String):(String,String) = { 
    if(rs==null || rs.trim.isBlank || !rs.contains(":")) return ("","")
    val (r,s) = rs.split(":").toList match { 
      case r::s::Nil => (r,s)
      case r::s::_ => (r,s)
      case _ => ("","")
    }
    if(r.isEmpty() || s.isEmpty()) return ("","")

    recover(m,r,s)
  }
}


