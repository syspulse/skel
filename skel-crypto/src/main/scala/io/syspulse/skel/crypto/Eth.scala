package io.syspulse.skel.crypto

import scala.util.{Try,Success,Failure}

import collection.JavaConverters._
import java.math.BigInteger

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey

import java.security.Security
import org.web3j.crypto.{ECKeyPair,ECDSASignature,Sign,Credentials,WalletUtils,Bip32ECKeyPair,MnemonicUtils,Keys}
import org.web3j.utils.{Numeric}

import io.syspulse.skel.util.Util

object Eth {
  
  def presig(m:String) = {val p = "\u0019Ethereum Signed Message:\n" + m.size; Hash.keccak256(p + m)}

  def normalize(kk:ECKeyPair):(String,String) = {
    val skb = kk.getPrivateKey().toByteArray
    val sk:Array[Byte] = skb.size match {
      case 31 => skb.toArray.+:(0)
      case 32 => skb
      case 33 => skb.drop(1)
      case _ => Array.fill(32 - skb.size)(0.toByte) ++ skb
    }
    val pkb = kk.getPublicKey().toByteArray
    val pk:Array[Byte] = pkb.size match {
      case 63 => pkb.toArray.+:(0)
      case 64 => pkb
      case 65 => pkb.drop(1)
      case _ => Array.fill(64 - pkb.size)(0.toByte) ++ pkb
    }
    (Util.hex(sk),Util.hex(pk)) 
  }

  def generate(sk:String):(String,String) = { 
    val kk = ECKeyPair.create(Numeric.hexStringToByteArray(sk))
    println(s"kk => ${kk.getPrivateKey()}")
    normalize(kk)
  }

  // generate random
  def generate:(String,String) = { 
    val kk = Keys.createEcKeyPair(); 
    normalize(kk)
  }

  def address(pk:String):String = Util.hex(Hash.keccak256(Numeric.hexStringToByteArray(pk)).takeRight(20))

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
      // I have no idea why web3j adds extra 00 to make PK 65 bytes !?
      Success((Util.hex(c.getEcKeyPair().getPrivateKey().toByteArray),Util.hex(c.getEcKeyPair().getPublicKey().toByteArray.takeRight(64))))
    }catch {
      case e:Exception => Failure(e)
    }
  }

  def readMnemonic(mnemonic:String,mnemoPass:String = null):Try[(String,String)] = {
    try {
      val c = WalletUtils.loadBip39Credentials(mnemoPass, mnemonic)
      // I have no idea why web3j adds extra 00 to make PK 65 bytes !?
      Success((Util.hex(c.getEcKeyPair().getPrivateKey().toByteArray),Util.hex(c.getEcKeyPair().getPublicKey().toByteArray.takeRight(64))))
    }catch {
      case e:Exception => Failure(e)
    }
  }

  def readMnemonicDerivation(mnemonic:String,derivation:String, mnemoPass:String = null):Try[(String,String)] = {
    // def   m/44'/60'/0'/1
    //       m/44'/60'/0'/0
    val ss = derivation.split("/")
    
    if(ss.size < 2) return Failure(new Exception(s"invalid derivation path: '${derivation}'"))
    if(ss(0) != "m") return Failure(new Exception(s"invalid derivation path start: '${derivation}'"))

    val derivationPath = ss.tail.foldLeft(Array[Int]())( (path,part) => {
      val bits = 
        if(part.endsWith("'")) 
          part.stripSuffix("'").toInt | Bip32ECKeyPair.HARDENED_BIT 
        else 
          part.toInt
      path :+ bits
    }) :+ 0

    //println(s"${derivationPath.toList}")
    //val derivationPath = Seq(44 | Bip32ECKeyPair.HARDENED_BIT, 60 | Bip32ECKeyPair.HARDENED_BIT, 0 | Bip32ECKeyPair.HARDENED_BIT, 0, 0).toArray
    //println(s"${Seq(44 | Bip32ECKeyPair.HARDENED_BIT, 60 | Bip32ECKeyPair.HARDENED_BIT, 0 | Bip32ECKeyPair.HARDENED_BIT, 0, 0).toList}")

    try {
      val master = Bip32ECKeyPair.generateKeyPair(MnemonicUtils.generateSeed(mnemonic, mnemoPass));
      val  derived = Bip32ECKeyPair.deriveKeyPair(master, derivationPath);

      val c = Credentials.create(derived)
      // I have no idea why web3j adds extra 00 to make PK 65 bytes !?
      Success((Util.hex(c.getEcKeyPair().getPrivateKey().toByteArray),Util.hex(c.getEcKeyPair().getPublicKey().toByteArray.takeRight(64))))
    }
    catch {
      case e:Exception => Failure(e)
    }
  }
}


