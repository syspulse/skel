// Testing Curve25519 by https://github.com/gherynos/secrete
// 
import coursierapi.MavenRepository
interp.repositories.update(interp.repositories() ::: List(MavenRepository.of(s"""file://${System.getenv("HOME")}/.m2/repository""")))
import $ivy.`net.nharyes:secrete:1.0.3`
import net.nharyes.secrete.ecies._
import java.security.KeyPair
import java.util.Random
import net.nharyes.secrete.curve.Curve25519KeyPairGenerator

val u2 = Curve25519KeyPairGenerator.generateKeyPair()
val text = "Text message"
val message:ECIESMessage = ECIESHelper.encryptData(u2.getPublic(), text);
val data = ECIESHelper.decryptMessage(u2.getPrivate(), message)
new String(data, ECIESHelper.ENCODING)