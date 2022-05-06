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
import javax.crypto.KeyAgreement;

Security.addProvider(new BouncyCastleProvider())
val kpGen = KeyPairGenerator.getInstance("ECDH", "BC");
val ecSpec = ECNamedCurveTable.getParameterSpec("secp256k1");
kpGen.initialize(ecSpec, new SecureRandom())

val u1 = kpGen.generateKeyPair()
val u2 = kpGen.generateKeyPair()

val ka1 = KeyAgreement.getInstance("ECDH", "BC")
ka1.init(u1.getPrivate)
ka1.doPhase(u2.getPublic, true)
val secret1 = ka1.generateSecret()

val ka2 = KeyAgreement.getInstance("ECDH", "BC")
ka2.init(u2.getPrivate)
ka2.doPhase(u1.getPublic, true)
val secret2 = ka2.generateSecret()

println(s"${Hex.toHexString(secret1)}\n${Hex.toHexString(secret2)}")