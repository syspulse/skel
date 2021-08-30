package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import collection.JavaConverters._
import java.math.BigInteger

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;

import org.web3j.crypto.{ECKeyPair,ECDSASignature,Sign,Credentials,WalletUtils}
import org.web3j.utils.{Numeric}

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

  def verify(m:String,sig:String,pk:String):Boolean = {
    val rs = Eth.fromSig(sig)
    val signature = new ECDSASignature(new BigInteger(Numeric.hexStringToByteArray(rs._1)),new BigInteger(Numeric.hexStringToByteArray(rs._2)))
    val h = presig(m)
    val r1 = Sign.recoverFromSignature(0,signature,h)
    val r2 = Sign.recoverFromSignature(1,signature,h)
    
    Util.hex(r1.toByteArray) == pk || Util.hex(r2.toByteArray) == pk
  }

  // return (SK,PK)
  def readKeystore(keystorePass:String,keystoreFile:String):Try[(String,String)] = {
    try {
      val c = WalletUtils.loadCredentials(keystorePass, keystoreFile)
      Success((Util.hex(c.getEcKeyPair().getPrivateKey().toByteArray),Util.hex(c.getEcKeyPair().getPublicKey().toByteArray)))
    }catch {
      case e:Exception => Failure(e)
    }
  }

  def readMnemonic(mnemonic:String,mnemoPass:String = null):Try[(String,String)] = {
    try {
      val c = WalletUtils.loadBip39Credentials(mnemoPass, mnemonic)
      Success((Util.hex(c.getEcKeyPair().getPrivateKey().toByteArray),Util.hex(c.getEcKeyPair().getPublicKey().toByteArray)))
    }catch {
      case e:Exception => Failure(e)
    }
  }
}


