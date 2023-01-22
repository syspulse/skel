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
import io.syspulse.skel.otp.store._
import io.syspulse.skel.otp.server._
import io.syspulse.skel.config.Configuration
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll

class TestSpecRandom extends ServerTestable("/api/v1/otp",Seq(
        (OtpRegistry(new OtpStoreMem),"OtpRegistry",(a,ac) => new OtpRoutes(a)(ac) ),
      )) with HttpServiceTest with BeforeAndAfterAll with DataTestable {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._  

  "OtpRoutes" should s"return Random OTP (GET ${uri}/random)" in {
      val request = Get(uri = s"${uri}/random")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpRandomRes]
        rsp.secret.size should ===(32)
      }
    }

  "OtpRoutes" should s"return Random OTP with QR data(GET ${uri}/random)" in {
      val otpRandom = OtpRandomReq(Some("app3"), Some("http://service3"))
      val otpEntity = Marshal(otpRandom).to[MessageEntity].futureValue 
      
      val request = Get(uri = s"${uri}/random").withEntity(otpEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpRandomRes]
        rsp.secret.size should ===(32)
        rsp.qrImage.size should !==(0)
        rsp.qrImage.startsWith("data:image/gif;base64") should ===(true)
      }
    }

}