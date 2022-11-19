package io.syspulse.skel.crypto

import scala.jdk.CollectionConverters

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;

object Hash {

  def keccak256(d:Array[Byte]) = { val kd = new Keccak.Digest256(); kd.update(d,0,d.size); kd.digest }
  def keccak256(m:String) = { val kd = new Keccak.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }
  def sha3(m:String) = { val kd = new SHA3.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }

    // time attack resistant compare
  def hashCompare(s1:String,s2:String) = { 
    var r = 0 
    val h1 = s1.padTo(128,'_'); val h2=s2.padTo(128,'_'); for{ i <- 0 to h1.size-1 } yield r = r | h1(i) ^ h2(i);
    r
  }
}


