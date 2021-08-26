

import $ivy.`org.web3j:crypto:5.0.0`
import org.web3j.crypto._
import org.web3j.utils._
import collection.JavaConverters._
import org.bouncycastle.jcajce.provider.digest.Keccak;

import org.bouncycastle.util.io.pem.PemReader
import java.security.KeyFactory
import java.security.security
import java.security.spec.PKCS8EncodedKeySpec

import java.security.spec.ECGenParameterSpec

import java.io.StringReader
import java.io.FileReader
import scala.io.Source

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
hex(ecKP1.getPublicKey.toByteArray)
