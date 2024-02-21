package io.syspulse.skel.auth.jwt

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import scala.util.Success

import io.syspulse.skel.util.Util
import pdi.jwt.JwtAlgorithm
import io.syspulse.skel.auth.permissions.Permissions
import io.syspulse.skel.auth.permissions.DefaultPermissions

class AuthJwtSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath
  
  val RSA_SK_PKCS8="""
  -----BEGIN PRIVATE KEY-----
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
Nk+lCFWgz3ifnnlaJ/3yPbHAsZSJ
-----END PRIVATE KEY-----
  """

  val RSA_PK = """
  -----BEGIN PUBLIC KEY-----
MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAybdmwbiALkuBTkMTZnQg
e49wdsUMN03d9SyNjJSpPBkjw54ymfPNM2dYzc9R3YeGRX8Hlpsnm+OaAHKBIQEh
MeglVqs5TCWLamXupxwpODmZudqZqS49ANWJhiKba9mAHYKbT/p+/JsH2T1+Hi7g
+WTuBvvJ5n6v5usGqRNuzp8kgGxO9b/jKKVBjk4ePvGGlvhMpOO87prKQ/YMiFkm
2/U0aJxfgBCutlSyuPTaKBkTphCqMYRt9yC6iOQsMVjDZgZWv6oX6x+LCrQVy7wq
GeX5svahAhBzBLjFVSJL/nsNuu52+W0db+B6H829BF93CpwXanO2kIzHnfQ1kB2l
Vs8aUXRNB6E4QE7jh+UFEZco2vIoc6qUy6wmnYNxg3hU0ojEUMbSeKdBzlpTmZ9I
E5fo4W9ColWHtW1xlyL48vrpQ0aMBsk/eTVlHfGD3YdqR39QbE+ueD+0nP9o8avP
QLq+lf/EibsLpQWUyZkgp5K5b6wv9wCxyqQ7Pbn5SFz8rMRMTikqovc46pM7sEvb
CfHTiQJwar9nyGOvpfDAp1/LAthCyzSVORrdkgwbQB41daaLd6RrX6+M7Lmx8fM9
YAvHGnDuIePrOPg9N4T8F1QO8V3WAvyH20SuRgt3ezI19+gAaOjyn01GR8NwTUX0
Omm8MDxBwYD4SZJrjkTsSOMCAwEAAQ==
-----END PUBLIC KEY-----
  """

  val RSA_PK_2 = """
  -----BEGIN PUBLIC KEY-----
MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAybdmwbiALkuBTkMTZnQg
e49wdsUMN03d9SyNjJSpPBkjw54ymfPNM2dYzc9R3YeGRX8Hlpsnm+OaAHKBIQEh
MeglVqs5TCWLamXupxwpODmZudqZqS49ANWJhiKba9mAHYKbT/p+/JsH2T1+Hi7g
+WTuBvvJ5n6v5usGqRNuzp8kgGxO9b/jKKVBjk4ePvGGlvhMpOO87prKQ/YMiFkm
2/U0aJxfgBCutlSyuPTaKBkTphCqMYRt9yC6iOQsMVjDZgZWv6oX6x+LCrQVy7wq
GeX5svahAhBzBLjFVSJL/nsNuu52+W0db+B6H829BF93CpwXanO2kIzHnfQ1kB2l
Vs8aUXRNB6E4QE7jh+UFEZco2vIoc6qUy6wmnYNxg3hU0ojEUMbSeKdBzlpTmZ9I
E5fo4W9ColWHtW1xlyL48vrpQ0aMBsk/eTVlHfGD3YdqR39QbE+ueD+0nP9o8avP
QLq+lf/EibsLpQWUyZkgp5K5b6wv9wCxyqQ7Pbn5SFz8rMRMTikqovc46pM7sEvb
CfHTiQJwar9nyGOvpfDAp1/LAthCyzSVORrdkgwbQB41daaLd6RrX6+M7Lmx8fM9
YAvHGnDuIePrOPg9N4T8F1QO8V3WAvyH20SuRgt3ezI19+gAaOjyn01GR8NwTUX1
Omm8MDxBwYD4SZJrjkTsSOMCAwEAAQ==
-----END PUBLIC KEY-----
  """

  val RSA_SK_RAW = """
  0x30820941020100300d06092a864886f70d01010105000482092b308209270201000282020100c9b766c1b8802e4b814e43136674207b8f7076c50c374dddf52c8d8c94a93c1923c39e3299f3cd336758cdcf51dd8786457f07969b279be39a00728121012131e82556ab394c258b6a65eea71c29383999b9da99a92e
3d00d58986229b6bd9801d829b4ffa7efc9b07d93d7e1e2ee0f964ee06fbc9e67eafe6eb06a9136ece9f24806c4ef5bfe328a5418e4e1e3ef18696f84ca4e3bcee9aca43f60c885926dbf534689c5f8010aeb654b2b8f4da281913a610aa31846df720ba88e42c3158c3660656bfaa17eb1f8b0ab415cbbc2a19e5f9b2f6a102107
304b8c555224bfe7b0dbaee76f96d1d6fe07a1fcdbd045f770a9c176a73b6908cc79df435901da556cf1a51744d07a138404ee387e505119728daf22873aa94cbac269d8371837854d288c450c6d278a741ce5a53999f481397e8e16f42a25587b56d719722f8f2fae943468c06c93f7935651df183dd876a477f506c4fae783fb4
9cff68f1abcf40babe95ffc489bb0ba50594c99920a792b96fac2ff700b1caa43b3db9f9485cfcacc44c4e292aa2f738ea933bb04bdb09f1d38902706abf67c863afa5f0c0a75fcb02d842cb3495391add920c1b401e3575a68b77a46b5faf8cecb9b1f1f33d600bc71a70ee21e3eb38f83d3784fc17540ef15dd602fc87db44ae4
60b777b3235f7e80068e8f29f4d4647c3704d45f43a69bc303c41c180f849926b8e44ec48e3020301000102820200593427d7077678029ffe321f84315aa409bcdc692b8bd17d948ccd541bf1f347d2cf1148a35873a885cd79c673179a75408a7a9a99a69c83bc78f22a34a4413fc6bfac623006829bf2c37d149d9984e9c417b2
9a3d7cdb31613d5dfa33ed2fd43e38a1e26e8e75bb416f4a885964b5ccd45b86ef164c3e3e1fca53b9b5844d0f879edb46d7dab1e429a0a203ddd04c130ef46340900ddf1ec8cfe1218db28f42ba5f20dbf647b618ed3ceb846b93862400d271903231b447934baa79ed292d6dcbd285ce60b40f2075e697d903ae04984b616f492
dceb1e38b75029f317995e26921ff8ed1a2f55fa24f9f7ce657e6b7c8135b2f92586bf835d69e4689a2f2987622eb553af8961dce8e3e9f17229ecf8cdce6df6cdf20642b1a894617d8c6fa5d40685581e9a778a2828a7ff820f8763ae9038255e9b80e7d8f9849e4b2fc6ddee67c1d8ed1e1f961a343a9d2d7d0c59a74e62a95b7
8b9d9e9a6832dbe2ef5c097d24c01a467abbd12b29defa87687d90ee097c9f12c9c4a6b117ec90cb57cf1c1506215e471f415cd25a98683574999f685f07eb4be390bb6a71b4661d47dc03dba7beaf3bfe0421fcdc4517ab59dac6fe8227ee86d7366fe8687809d4af164730329ee479d46ac305a3cbb99a0f2a650990a3bf2414b
5d4e632cd34f1607fa96c58a02d583bcc2ee9ba7b75ea1f68cdccd0391bd9c324ae868ab978dcddd10282010100e5516f2ea61171d55c7598e442d2351330e7fcef2270024bf181976289f996a3f3b8d2a3bc6dbe65d125de7ee45caea6e73f77f4e6c905dd8adbb53166425717925ccd992675f78eea3ea63200b150478e89bd48
d27daa4a9510c8b9a3364824e9c7ffbc1e3e1f4e0598f1a9fdee4f4b0da04b93d57e19fa945397319937ee4ac89d3ca49fe923377ff7ada6948284e94c3b305543151dda1db6b831446b596237021e4e28a7cf2223036c3854043f0c1e3bf055ea42bf7d0dee87be92b2355f950df862463ff952b819b7b4212e63ed321591763c1
2b81021468664b52b980fa000a63995088e2535ac02311557d0204acfcb2be4898d8706d51317e7454dd70282010100e12fcfbd2db877b9c4233b4b0ec6fe0fde9ca036284618db2c688820c9de955cf44512feba051b2fea598ae0c034718a9f3fe7f9275cbf5da859f71b4402afe0e84e9f7ef15c4416917dba1c33bfbb2463d2
5c533ddb3c53439f298c33ff3df1d0fcf65b66c7cada8b076b30f36afdbb7ed5e9011b0828c4b2e09420ab2e711c744e9d21144d3bc3ed23185daa43dc9331ab82a1883f02c3c072967a97ca534bee1156086e17d9267f25f7f2df06b7f10830ab26aaec750b95d8707313f45ea9d39b86ba1891828974f0e79e712938e69ad2aaf
4b4590576c933a2dc60c56320d50cf2054ce12bbd8823669bf1cca26399eb1f63ad4f2347456e869fb54f03d5028201006412bdaa65de8706cd6aa46638a2f376848938e042ef0e9185bb400d48991b1c41680ec4559eaed0500d6ea38194d0c31577a529c2ed4719ff57122394b4aebd22aa4d2ee091ea8efbba8b5c96e0ca5fef
dc3c36636563c3587a129eb09327b8b42d0b7554dd575bfd2559910db7c9f2bee78d13debf41271143c95f63b5fccfb5eca8624f46b68fa9d0b47f559fffd0afebc493f0ca6e549952fbdec434f4b4dfbc94ed16e0e48bc92bdd3657b3e2afaf220927f52b5bd57f16f136415e73dc5a77d6454cd2ee432304561ecdbc28ef8243d
74ef9b8518af87a867bbc7461cfafcb98bed842fb85517ed716047265e931bcacb562ee844cc0b68bb68abebbd10282010063e1be262e3a6d3883ac10e7b8f540c9af370fc515d609be659334163f1430721b92ebdafa8dbb426493350b7d95856b3439b75e073c97512694490bd9fdcb1f5a4b4d69940b15d460d24093b3cc6e68
b4b220eb773b60259a8fe570dfbb6e316d3e56d3b1e21ab31709c25ce30b68e21a5f2d4ba53ed83e6f0d3806c986fefadd07e5a03f8e065d6038b9dd09e1b1dbc4234bfcf41ee6ce06f58c9371f27741eda5d8e0425ce79fb0b419a7ab8f225592096c50e96d9a78fa0d17cba36537fbe1f5512c082bba97d7bbe7309b06eacb368
f0cef1f7380fdf03eac154e031f75f7aa174b8c8f2fc490b70b134e8c32d734f55a65ef0074286fbe978e0d70afc5028201006856c593eef27556daa86ff073604c901ec71300e2e9007f1ec9bbd02e9a3be37cb30a52a6a236fdc4005224a1a90ef1e5e8c492e6c9913e2ea2e58a2865b657efdfe961773f88501c3e413efaefbf
9182bcf4491c94184e1beb44297a651165c7db9678298491538283bce5c02c96de2ae4b51c6c4c7e84f8cda172c24450734dc421fdab7d10308715d4f55cdef35374658beaa0aa98ca6960aec615c51d455bfbce2ff4fb4fe22289543887c1827016ccf74f12c4a20138e75698252c37d985847e87cba0adfd7cd46fcc4525f35cb
d86616d2764f106c959768190d80bd8fb6d1c651dd6e8c24b989f364fa50855a0cf789f9e795a27fdf23db1c0b19489
  """
  .replaceAll("\\s+","")
  .replaceAll(System.lineSeparator(), "")

  "AuthJWT" should {

    "default secret is secure" in {      
      AuthJwt().getSecret() should !== ("")
    }

    "default algo is HS512" in {      
      val a1 = AuthJwt()
      a1.getAlgo() should === ("HS512")
      a1.getSecret() should !== ("")
    }

    "default validate JWT with defaults" in {      
      val j1 = AuthJwt().generateToken()
      AuthJwt().isValid(j1) should === (true)
    }

    "default generate JWT AccessToken" in {
      val a1 = AuthJwt()      
      val j1 = a1.generateAccessToken(Map("uid" -> DefaultPermissions.USER_ADMIN.toString))
      j1 should !== ("")
    }

    "fail JWT validation with a wrong secret" in {
      val a1 = new AuthJwt().withSecret("secret")
      val j1 = a1.generateToken()
      
      val a2 = new AuthJwt().withSecret("secret2")
      a2.isValid(j1) should === (false)
    }

        
    "default validate generated Admin token" in {
      val a1 = AuthJwt()
      val j1 = a1.generateAccessToken(Map("uid" -> DefaultPermissions.USER_ADMIN.toString))
      
      val a2 = AuthJwt()
      a2.isValid(j1) should === (true)
    }

    "not validate JWT token as Admin (expired)" in {
      val j1 = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiJ9.eyJleHAiOjE2NzQzNDMxODAsImlhdCI6MTY3NDMzOTU4MCwidWlkIjoiZmZmZmZmZmYtZmZmZi1mZmZmLWZmZmYtZmZmZmZmZmZmZmZmIn0.VaYS9CVgFKH9sw81TTWTuztG-zo4y7LAab7Sb_diQZMViMH9HZpHDMq0NGbt7mmvyhB1tsLnsv-AZMnUAtZMSw"
      AuthJwt().isValid(j1) should !== (true)
    }

    // ----------------------------------- RSA

    "generate JWT with RS512" in {
      val a1 = new AuthJwt().withPrivateKey(RSA_SK_PKCS8).withAlgo("RS512")
      val j1 = a1.generateToken()
      j1 should !== ("")
    }

    "validate JWT with RS512" in {
      val a1 = new AuthJwt().withPrivateKey(RSA_SK_PKCS8).withAlgo("RS512")
      val j1 = a1.generateToken()

      val a2 = new AuthJwt().withPublicKey(RSA_PK).withAlgo("RS512")
      a2.isValid(j1) should === (true)
    }

    "NOT validate JWT with wrong PublicKey" in {
      val a1 = new AuthJwt().withPrivateKey(RSA_SK_PKCS8).withAlgo("RS512")
      val j1 = a1.generateToken()

      val a2 = new AuthJwt().withPublicKey(RSA_PK_2).withAlgo("RS512")
      a2.isValid(j1) should === (false)
    }

    // ----------------------------------- Uri

    "create AuthJwt with default HMAC" in {
      val a1 = new AuthJwt().withUri("hs512://")
      
      a1.getAlgo() should === ("HS512")
      a1.getSecret() should !== ("")
    }

    "create AuthJwt with HS512" in {
      val a1 = new AuthJwt().withUri("hs512://secret1")
      
      a1.getAlgo() should === ("HS512")
      a1.getSecret() should === ("secret1")
    }
    
    "create AuthJwt with RS512 PrivateKey from PKCS8 file" in {
      val a1 = new AuthJwt().withUri(s"rs512://sk:pkcs8:${testDir}/RS512.key.pkcs8")
      
      a1.getAlgo() should === ("RS512")
      a1.getSecret() should === ("")
    }

    "create AuthJwt with RS512 PrivateKey from Raw hexdata" in {
      val a1 = new AuthJwt().withUri(s"rs512://sk:hex:${RSA_SK_RAW}")
      
      a1.getAlgo() should === ("RS512")
      a1.getSecret() should === ("")
    }

    "create AuthJwt with RS512 PublicKey from CER (X509) file" in {
      val a1 = new AuthJwt().withUri(s"rs512://pk:cer:${testDir}/RS512.key.pub")
      
      a1.getAlgo() should === ("RS512")
      a1.getPublicKey() should !== ("")
    }

    "create AuthJwt with RS512 PrivateKey and verify with its PublicKey" in {
      val a1 = new AuthJwt().withUri(s"rs512://sk:pkcs8:${testDir}/RS512.key.pkcs8")
      
      a1.getAlgo() should === ("RS512")
      a1.getSecret() should === ("")
      a1.getPublicKey() should !== ("")

      val j1 = a1.generateToken()
      a1.isValid(j1) should === (true)
    }

    "create AuthJwt with PublicKey from JWKS (Google store)" in {
      val a1 = new AuthJwt().withUri(s"jwks:https://www.googleapis.com/oauth2/v3/certs")
      
      a1.getAlgo() should (be("RS512") or be("RS256"))
      a1.getPublicKey() should !== ("")
      
    }

    "create AuthJwt with PublicKey from OpenId Config (Google)" in {
      val json = os.read(os.Path(testDir + "/google-openid.json",os.pwd))
      val a1 = AuthJwt.getPublicKeyFromOpenIdConf(json)
      
      a1(0)._1 should (be("RS512") or be("RS256"))
      a1(0)._2 should !== ("")      
    }

    "create AuthJwt with PublicKey from OpenId Url (Google)" in {
      val a1 = new AuthJwt().withUri(s"https://accounts.google.com/.well-known/openid-configuration")
      
      a1.getAlgo() should (be("RS512") or be("RS256"))
      a1.getPublicKey() should !== ("")      
    }
    
  }
}
