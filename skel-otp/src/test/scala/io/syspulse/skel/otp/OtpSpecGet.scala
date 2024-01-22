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
import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

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

class TestSpecGet extends ServerTestable("/api/v1/otp",Seq(
        (OtpRegistry(new OtpStoreMem),"OtpRegistry",(a,ac) => new OtpRoutes(a)(ac) ),
      )) with HttpServiceTest with BeforeAndAfterAll with DataTestable {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._  

  "OtpRoutes" should s"return NO otps if no present (GET ${uri})" in {
      val request = HttpRequest(uri = uri)
      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should === ("""{"otps":[]}""")
      }
    }

}
