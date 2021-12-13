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
  
  def presig(m:String):Array[Byte] = presig(m.getBytes())
  def presig(m:Array[Byte]):Array[Byte] = {val p = "\u0019Ethereum Signed Message:\n" + m.size; Hash.keccak256((Numeric.hexStringToByteArray(p) ++ m).toArray)}
  
  def normalize(b0:Array[Byte],sz:Int):String = {
    val b1:Array[Byte] = b0.size match {
      case _ if(b0.size == sz -1) => b0.toArray.+:(0)
      case `sz` => b0
      case _ if(b0.size == sz + 1)  => b0.drop(1)
      case _ => Array.fill(sz - b0.size)(0.toByte) ++ b0
    }
    Util.hex(b1)
  }

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
    normalize(kk)
  }

  // generate random
  def generate():(String,String) = { 
    val kk = Keys.createEcKeyPair(); 
    normalize(kk)
  }

  // derive new Secure Keys from PrivateKey
  def deriveKey(sk:String, msg:String, nonce:Long = -1L):String = {
    val sig = sign(msg,(if(nonce == -1L) msg else s"${msg}:${nonce}"))
    Util.sha256(sig)
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
    if(m==null) return ""
    sign(m.getBytes(),sk)
  }

  def sign(m:Array[Byte],sk:String):String = {
    if(m==null || sk==null || sk.isEmpty()) return ""
    
    val kk = ECKeyPair.create(Numeric.hexStringToByteArray(sk))
    toSig(kk.sign(presig(m)))
  }

  def verify(m:String,sig:String,pk:String):Boolean = verify(m.getBytes(),sig,pk)

  def verify(m:Array[Byte],sig:String,pk:String):Boolean = {
    if(m==null || sig==null || sig.isEmpty || pk==null || pk.isEmpty ) return false

    val rs = Eth.fromSig(sig)
    try {
        val signature = new ECDSASignature(new BigInteger(Numeric.hexStringToByteArray(rs._1)),new BigInteger(Numeric.hexStringToByteArray(rs._2)))
        val h = presig(m)
        val r1 = Sign.recoverFromSignature(0,signature,h)
        val r2 = Sign.recoverFromSignature(1,signature,h)
      
        //The right way is probably to migrate to signed BigInteger(1,r1.toByteArray)
        normalize(r1.toByteArray,64) == pk || normalize(r2.toByteArray,64) == pk
    } catch {
      case e:Exception => false
    }
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


