import $ivy.`org.web3j:crypto:5.0.0`

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

def hex(x: Seq[Byte]) = "0x"+x.toArray.map("%02x".format(_)).mkString
def keccak256(m:String) = { val kd = new Keccak.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }
def sha3(m:String) = { val kd = new SHA3.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }

def recover(m:String,r:String,s:String):(String,String) = { 
  val sig = new ECDSASignature(new BigInteger(Numeric.hexStringToByteArray("0x00"+r)),new BigInteger(Numeric.hexStringToByteArray("0x00"+s))) 
  val h = keccak256(m)
  (
    hex(Sign.recoverFromSignature(0,sig,h).toByteArray),
    hex(Sign.recoverFromSignature(1,sig,h).toByteArray)
  )
}

@main
def main(pemFile:String = "/dev/shm/sk.pem", count:Int = 0) = {

  Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

  val p = new PemReader(new FileReader(pemFile))
  val spec = new PKCS8EncodedKeySpec(p.readPemObject.getContent)

  // this works1
  val skHex = "0x"+hex(spec.getEncoded).substring(68,68+64)
  val ecKP1 = ECKeyPair.create(Numeric.hexStringToByteArray(skHex)) 
  val pk = hex(ecKP1.getPublicKey.toByteArray)
  //println(pk)

  var txt = ""
  while({txt = scala.io.StdIn.readLine(); txt}!=null) {
    val (m,r,s) = txt.split("\\s+").toList match { case m::r::s::Nil => (m,r,s)}
    //println(s"m=${m},r=${r},s=${s}")
    val pkR = recover(m,r,s)
    //println(s"${pk} == ${pkR}")
    if(pk.toLowerCase()!=pkR._1.toLowerCase() && 
       pk.toLowerCase()!=pkR._2.toLowerCase()) {
       println(s"ERR: ${m},${r},${s}: ${pk} != ${pkR}")
    } else println("OK")
  }    
}
