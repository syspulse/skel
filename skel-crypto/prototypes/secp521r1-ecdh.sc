import $ivy.`org.bouncycastle:bcprov-jdk15on:1.69`

import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.bouncycastle.jce.spec.IESParameterSpec;
import org.bouncycastle.util.encoders.Hex;
import java.security._
import javax.crypto.Cipher

Security.addProvider(new BouncyCastleProvider())
val kpGen = KeyPairGenerator.getInstance("EC", "BC");
val ecSpec = ECNamedCurveTable.getParameterSpec("secp521r1");
kpGen.initialize(ecSpec, new SecureRandom())
val keyPair = kpGen.generateKeyPair()
val publicKey = keyPair.getPublic()
val privateKey = keyPair.getPrivate()
val derivation = Hex.decode("101112131415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f505152535455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f");
val encoding = Hex.decode("303132333435363738393a3b3c3d3e3f404142434445464748494a4b4c4d4e4f707172737475767778797a7b7c7d7e7f808182838485868788898a8b8c8d8e8f")

val cipher1 = Cipher.getInstance("ECIESwithAES-CBC", "BC")
val params = new IESParameterSpec(derivation, encoding, 512, 256, Array.fill(16)(0.toByte))
cipher1.init(Cipher.ENCRYPT_MODE, publicKey, params, new SecureRandom())

val blocksize = cipher1.getBlockSize();
val msg = "Test message"

val output = Array.fill(4096)(0.toByte)
output.size
cipher1.doFinal(msg.getBytes, 0, msg.size, output)

val cipher2 = Cipher.getInstance("ECIESwithAES-CBC", "BC")
cipher2.init(Cipher.DECRYPT_MODE, privateKey, params, new SecureRandom())
val output2 = Array.fill(4096)(0.toByte)
val sz = cipher2.doFinal(output, 0, output.take(169).size, output2)
new String(output2.take(sz))
