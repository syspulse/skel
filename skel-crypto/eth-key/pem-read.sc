import $ivy.`org.web3j:crypto:5.0.0`

import collection.JavaConverters._

import org.web3j.crypto._
import org.web3j.utils._
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.io.pem.PemReader

import java.security.KeyFactory
import java.security.security
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.ECGenParameterSpec

import java.io.StringReader
import java.io.FileReader
import scala.io.Source

def hex(x: Seq[Byte]) = "0x"+x.toArray.map("%02x".format(_)).mkString
def keccak256(m:String) = { val kd = new Keccak.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }
def sha3(m:String) = { val kd = new SHA3.Digest256(); kd.update(m.getBytes,0,m.size); kd.digest }

Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider())

//val p = new PemReader(new FileReader("/dev/shm/sk.pkcs8"))
// this must be PKCS8 format !
val p = new PemReader(new StringReader(Source.fromFile("/dev/shm/sk.pem").getLines.mkString("\n")))
val p = new PemReader(new FileReader("/dev/shm/sk.pem"))

//val spec = new ECGenParameterSpec(p.readPemObject) 
val spec = new PKCS8EncodedKeySpec(p.readPemObject.getContent)

// this does not work?!
//val sk = KeyFactory.getInstance("ECDSA").generatePrivate(spec)

// this works1
val skHex = "0x"+hex(spec.getEncoded).substring(68,68+64)
val ecKP1 = ECKeyPair.create(Numeric.hexStringToByteArray(skHex)) 

val r = "0x07692DF435A2950B818910A4CF51536CB194C2F58E5FB5D337FE933F336F77DF"
// Pay attention at leading 0! s
val s = "0x0D2B09284019705AE0B0F1A4EA70AADCABE0AF94C6B9EB4082C4A33E595295047"

val sig = new ECDSASignature(new BigInteger(Numeric.hexStringToByteArray(r)),new BigInteger(Numeric.hexStringToByteArray(s))) 

hex(ecKP1.getPublicKey.toByteArray)

hex(Sign.recoverFromSignature(0,sig,keccak256("message\n")).toByteArray)
