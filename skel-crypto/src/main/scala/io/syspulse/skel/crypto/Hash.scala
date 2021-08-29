package io.syspulse.skel.crypto

import collection.JavaConverters._

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;

object Hash {

  def keccak256(m:String) = { val kd = new Keccak.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }
  def sha3(m:String) = { val kd = new SHA3.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }

}


