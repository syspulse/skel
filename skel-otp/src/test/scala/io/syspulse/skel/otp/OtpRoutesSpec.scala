package io.syspulse.skel.otp

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.RejectionHandler
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }


import io.jvm.uuid._

import akka.http.scaladsl.server.Route
import akka.util.ByteString
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import io.syspulse.skel.Server
import io.syspulse.skel.test.HttpServiceTest
import io.syspulse.skel.otp.OtpRegistry._

class OtpRoutesSpec extends HttpServiceTest {
//class OtpRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {
  
  // the Akka HTTP route testkit does not yet support a typed actor system (https://github.com/akka/akka-http/issues/2036)
  // so we have to adapt for now
  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem

  val uri = "/api/v1/otp"

  // Here we need to implement all the abstract members of OtpRoutes.
  // We use the real OtpRegistryActor to test it while we hit the Routes,
  // but we could "mock" it by implementing it in-place or by using a TestProbe
  // created with testKit.createTestProbe()
  val otpRegistry = testKit.spawn(OtpRegistry(new OtpStoreCache))
  var server = new Server {
    val (rejectionHandler,exceptionHandler) = getHandlers()
    val routes = getRoutes(
      rejectionHandler,exceptionHandler,
      uri,
      Seq(),
      Seq(new OtpRoutes(otpRegistry).routes)
    )
  }
  lazy val routes = server.routes

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._
  
  // assumes tests are not run in parallel
  var testId:UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  var otpId1:UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  var otpId2:UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")

  var userId1:UUID = UUID.fromString("11111111-1111-1111-1111-000000000001")
  
  var userId2:UUID = UUID.fromString("22222222-2222-2222-2222-000000000002")
  var userId2Otp1:UUID = UUID.random
  var userId2Otp2:UUID = UUID.random

  "OtpRoutes" should {
    s"return NO otps if no present (GET ${uri})" in {
      val request = HttpRequest(uri = uri)

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        entityAs[String] should === ("""{"otps":[]}""")
      }
    }
    s"be able to add Otp (POST ${uri})" in {
      val otpCreate = OtpCreate(userId1, "secret", "app1", "http://service", Some(42))
      val otpEntity = Marshal(otpCreate).to[MessageEntity].futureValue 

      val request = Post(uri).withEntity(otpEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[OtpCreatePerformed]
        // otp is created with random UUID !
        rsp.secret === (s"""secret""")
        
        testId = rsp.id.get
      }
    }

    s"return created otp (GET ${uri})" in {
      val request = Get(uri = s"${uri}/${testId}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Otp]
        rsp.id should ===(testId)
        rsp.secret should ===("secret")
        rsp.name should ===("app1")
        rsp.uri should ===("http://service")
        rsp.period should ===(42)
      }
    }

    s"return 1 otps (GET ${uri})" in {
      val request = Get(uri = s"${uri}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Otps]

        rsp.otps.size should ===(1)
      }
    }

    s"be able to add 2 Otp to User ${userId2} (POST ${uri})" in {
      val otpCreate1 = OtpCreate(userId2,"25gs67pxghrop232", "app1", "http://service1", Some(30))
      val otpEntity1 = Marshal(otpCreate1).to[MessageEntity].futureValue 

      val otpCreate2 = OtpCreate(userId2,"VZUUQQXB2BJHMDFLD46AWJDNEJKJ2MPV", "app2", "http://service2", Some(30))
      val otpEntity2 = Marshal(otpCreate2).to[MessageEntity].futureValue 


      val request1 = Post(s"${uri}").withEntity(otpEntity1)
      val request2 = Post(s"${uri}").withEntity(otpEntity2)

      request1 ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[OtpCreatePerformed]
        // otp is created with random UUID !
        rsp.secret === "25gs67pxghrop232"
        userId2Otp1 = rsp.id.get
      }

      request2 ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[OtpCreatePerformed]
        // otp is created with random UUID !
        rsp.secret === "VZUUQQXB2BJHMDFLD46AWJDNEJKJ2MPV"
        userId2Otp2 = rsp.id.get
      }
    }

    s"get Otp for User ${userId2} (GET ${uri}/user)" in {
      val request = Get(uri = s"${uri}/user/${userId2}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Otps]

        rsp.otps.size should ===(2)

        val otp1 = rsp.otps(0)
        val otp2 = rsp.otps(1)

        otp1.id should === (userId2Otp1)
        otp1.secret should ===("25gs67pxghrop232")
        otp1.name should ===("app1")
        otp1.uri should ===("http://service1")
        otp1.period should ===(30)

        otp2.id should === (userId2Otp2)
        otp2.secret should ===("VZUUQQXB2BJHMDFLD46AWJDNEJKJ2MPV")
        otp2.name should ===("app2")
        otp2.uri should ===("http://service2")
        otp2.period should ===(30)
      }
    }

    s"return Code for User(${userId2}), Otp(${userId2Otp1}) (GET ${uri}/${userId2Otp1}/code)" in {
      val request = Get(uri = s"${uri}/${userId2Otp1}/code")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[GetOtpCodeResponse]

        info(s"User(${userId2}), Otp(${userId2Otp1}: Code=${rsp.code}")
        rsp.code should !==(Some(""))
        rsp.code.get.size should ===(6)
      }
    }

    def authCode(secret:String):String = {
      import ejisan.kuro.otp._
      val otpkey = OTPKey.fromBase32(secret.toUpperCase,false)
      val totpSHA1 = TOTP(OTPAlgorithm.SHA1, 6, 30, otpkey)
      val code = totpSHA1.generate()
      code 
    }

    s"verify Correct Code as Authorized for User(${userId2}), Otp(${userId2Otp1}) (GET ${uri}/${userId2Otp1}/code/{code})" in {
      val codeUser = authCode("25gs67pxghrop232")

      val request = Get(uri = s"${uri}/${userId2Otp1}/code/${codeUser}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        info(s"response=${response}\n'${entityAs[String]}'")

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[GetOtpCodeVerifyResponse]

        rsp.code should ===(codeUser)
        rsp.authorized should ===(true)
      }
    }

    s"verify InCorrect Code as UnAuthorized for User(${userId2}), Otp(${userId2Otp1}) (GET ${uri}/${userId2Otp1}/code/{code})" in {
      val codeUser = "123456"

      val request = Get(uri = s"${uri}/${userId2Otp1}/code/${codeUser}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[GetOtpCodeVerifyResponse]

        rsp.code should ===(codeUser)
        rsp.authorized should ===(false)
      }
    }

    s"be able to remove otps (DELETE ${uri})" in {
      val request = Delete(uri = s"${uri}/${testId}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        //entityAs[String] should ===("""{"description":"Otp deleted."}""")
        val rsp = entityAs[OtpActionPerformed]
        rsp.description should startWith(s"""deleted""")
      }
    }

    s"return Otp after Delete ${userId2Otp1} (GET ${uri})" in {
      val request = Get(uri = s"${uri}/${userId2Otp1}")
      request ~> Route.seal(routes) ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
      }
    }

    s"return 404 and NO Otp(${testId}) after Delete ${testId} (GET /otp)" in {
      val request = Get(uri = s"${uri}/${testId}")
      request ~> Route.seal(routes) ~> check {
        status should ===(StatusCodes.NotFound)
        // val f  = response.entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String)
        // val s = Await.result(f,Duration.Inf)
        info(s"${response}\n'${entityAs[String]}'")
        info(s"body='${getBody()}'")
        contentType should ===(ContentTypes.`application/json`)
      }
    }
  }
}
