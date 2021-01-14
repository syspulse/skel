package io.syspulse.skel.otp

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import io.jvm.uuid._

import io.syspulse.skel.otp.OtpRegistry._

class OtpRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {
  
  // the Akka HTTP route testkit does not yet support a typed actor system (https://github.com/akka/akka-http/issues/2036)
  // so we have to adapt for now
  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem

  // Here we need to implement all the abstract members of OtpRoutes.
  // We use the real OtpRegistryActor to test it while we hit the Routes,
  // but we could "mock" it by implementing it in-place or by using a TestProbe
  // created with testKit.createTestProbe()
  val otpRegistry = testKit.spawn(OtpRegistry(new OtpStoreCache))
  lazy val routes = new OtpRoutes(otpRegistry).routes

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._
  
  // assumes tests are not run in parallel
  var testId:UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  "OtpRoutes" should {
    "return no otps if no present (GET /otp)" in {
      val request = HttpRequest(uri = "/otp")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        entityAs[String] should ===("""{"otps":[]}""")
      }
    }
    "be able to add otps (POST /otp)" in {
      val otpCreate = OtpCreate("secret", "app1", "http://service", Some(42))
      val otpEntity = Marshal(otpCreate).to[MessageEntity].futureValue 

      val request = Post("/otp").withEntity(otpEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[OtpActionPerformed]
        // otp is created with random UUID !
        rsp.description should startWith(s"""created""")
        testId = rsp.id.get
      }
    }

    "return created otp (GET /otp)" in {
      val request = Get(uri = s"/otp/${testId}")

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

    "return 1 otps (GET /otp)" in {
      val request = Get(uri = s"/otp/")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Otps]

        rsp.otps.size should ===(1)
      }
    }

    "be able to remove otps (DELETE /otp)" in {
      val request = Delete(uri = s"/otp/${testId}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        //entityAs[String] should ===("""{"description":"Otp deleted."}""")
        val rsp = entityAs[OtpActionPerformed]
        rsp.description should startWith(s"""deleted""")
      }
    }
  }
}
