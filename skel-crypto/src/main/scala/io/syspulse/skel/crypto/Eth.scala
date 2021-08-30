package io.syspulse.skel.crypto

import collection.JavaConverters._
import java.math.BigInteger

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;

import org.web3j.crypto.{ECKeyPair,ECDSASignature,Sign}
import org.web3j.utils.Numeric

import io.syspulse.skel.util.Util

object Eth {
  def presig(m:String) = {val p = "\u0019Ethereum Signed Message:\n" + m.size; Hash.keccak256(p + m)}

  // compatible with OpenSSL signature encoding
  def fromSig(rs:String):(String,String) = { 
    if(rs==null || rs.trim.isBlank || !rs.contains(":")) return ("","")
    val (r,s) = rs.split(":").toList match { 
      case r::s::Nil => (r,s)
      case r::s::_ => (r,s)
      case _ => ("","")
    }
    (r,s)
  }

  def toSig(sig: ECDSASignature):String = s"${Util.hex(sig.r.toByteArray)}:${Util.hex(sig.s.toByteArray)}"

  def sign(m:String,sk:String):String = {
    val kk = ECKeyPair.create(Numeric.hexStringToByteArray(sk))
    toSig(kk.sign(presig(m)))
  }

  def verify(m:String,sig:String,pk:String) = {
    val rs = Eth.fromSig(sig)
    val signature = new ECDSASignature(new BigInteger(Numeric.hexStringToByteArray(rs._1)),new BigInteger(Numeric.hexStringToByteArray(rs._2)))
    val h = presig(m)
    val r1 = Sign.recoverFromSignature(0,signature,h)
    val r2 = Sign.recoverFromSignature(1,signature,h)
    
    Util.hex(r1.toByteArray) == pk || Util.hex(r2.toByteArray) == pk
  }
}


