package io.syspulse.skel.otp

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.server.RejectionHandler
import akka.http.scaladsl.server.Route
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.javadsl.Behaviors

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }
import org.scalatest.BeforeAndAfter

import io.jvm.uuid._

import akka.util.ByteString
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import io.syspulse.skel.Server
import io.syspulse.skel.test.HttpServiceTest
import io.syspulse.skel.otp.OtpRegistry._
import io.syspulse.skel.config.Configuration
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll

class TestSpecCreate extends ServerTestable("/api/v1/otp",Seq(
        (OtpRegistry(new OtpStoreMem),"OtpRegistry",(a,ac) => new OtpRoutes(a)(ac) ),
      )) with HttpServiceTest with BeforeAndAfterAll with DataTestable {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._  

  "OtpRoutes" should s"be able to add Otp (POST ${uri})" in { 
      val otpCreate = OtpCreateReq(userId1, "secret", "app1", "user1@email.com", Some("http://service"), Some(42))
      val otpEntity = Marshal(otpCreate).to[MessageEntity].futureValue 
      val request = Post(uri).withEntity(otpEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpCreateRes]
        // otp is created with random UUID !
        rsp.secret should === (s"""secret""")
        testId = rsp.id.get
      }
    }

   "OtpRoutes" should s"return created otp (GET ${uri})" in {
      val request = Get(uri = s"${uri}/${testId}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Otp]
        rsp.id should ===(testId)
        rsp.secret should ===("secret")
        rsp.name should ===("app1")
        rsp.account should ===("user1@email.com")
        rsp.issuer should ===("http://service")
        rsp.period should ===(42)
      }
    }

    "OtpRoutes" should s"return 1 otps (GET ${uri})" in {
      val request = Get(uri = s"${uri}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[Otps]
        rsp.otps.size should ===(1)
      }
    }


}
