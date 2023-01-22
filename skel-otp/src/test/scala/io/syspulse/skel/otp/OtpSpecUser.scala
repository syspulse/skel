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

class TestSpecUser extends ServerTestable("/api/v1/otp",Seq(
        (OtpRegistry(new OtpStoreMem),"OtpRegistry",(a,ac) => new OtpRoutes(a)(ac) ),
      )) with HttpServiceTest with BeforeAndAfterAll with DataTestable {

  def getOtpCode(secret:String):String = {
      import ejisan.kuro.otp._
      val otpkey = OTPKey.fromBase32(secret.toUpperCase,false)
      val totpSHA1 = TOTP(OTPAlgorithm.SHA1, 6, 30, otpkey)
      val code = totpSHA1.generate()
      code 
  }
  
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import OtpJson._

  "OtpSpec" should s"be able to add 2 Otp to User ${userId2} (POST ${uri})" in {
      val otpCreate1 = OtpCreateReq(userId2,"25gs67pxghrop232", "app1", "user2@email.com", Some("http://service1"), Some(30))
      val otpEntity1 = Marshal(otpCreate1).to[MessageEntity].futureValue 

      val otpCreate2 = OtpCreateReq(userId2,"VZUUQQXB2BJHMDFLD46AWJDNEJKJ2MPV", "app2", "user2@email.com", Some("http://service2"), Some(30))
      val otpEntity2 = Marshal(otpCreate2).to[MessageEntity].futureValue 


      val request1 = Post(s"${uri}").withEntity(otpEntity1)
      val request2 = Post(s"${uri}").withEntity(otpEntity2)

      request1 ~> routes ~> check {
        status should ===(StatusCodes.Created)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpCreateRes]
        // otp is created with random UUID !
        rsp.secret === "25gs67pxghrop232"
        userId2Otp1 = rsp.id.get
      }

      request2 ~> routes ~> check {
        status should ===(StatusCodes.Created)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpCreateRes]
        // otp is created with random UUID !
        rsp.secret === "VZUUQQXB2BJHMDFLD46AWJDNEJKJ2MPV"
        userId2Otp2 = rsp.id.get
      }
    }

  "OtpSpec" should s"get Otp for User ${userId2} (GET ${uri}/user)" in {
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
        otp1.account should ===("user2@email.com")
        otp1.issuer should ===("http://service1")
        otp1.period should ===(30)

        otp2.id should === (userId2Otp2)
        otp2.secret should ===("VZUUQQXB2BJHMDFLD46AWJDNEJKJ2MPV")
        otp2.name should ===("app2")
        otp2.account should ===("user2@email.com")
        otp2.issuer should ===("http://service2")
        otp2.period should ===(30)
      }
    }

  "OtpSpec" should s"return Code for User(${userId2}), Otp(${userId2Otp1}) (GET ${uri}/${userId2Otp1}/code)" in {
      val request = Get(uri = s"${uri}/${userId2Otp1}/code")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpCodeRes]
        info(s"User(${userId2}), Otp(${userId2Otp1}: Code=${rsp.code}")
        rsp.code should !==(Some(""))
        rsp.code.get.size should ===(6)
      }
    }

  "OtpSpec" should s"verify Correct Code as Authorized for User(${userId2}), Otp(${userId2Otp1}) (GET ${uri}/${userId2Otp1}/code/{code})" in {
      val codeUser = getOtpCode("25gs67pxghrop232")

      val request = Get(uri = s"${uri}/${userId2Otp1}/code/${codeUser}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        info(s"response=${response}\n'${entityAs[String]}'")
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpCodeVerifyRes]
        rsp.code should ===(codeUser)
        rsp.authorized should ===(true)
      }
    }

  "OtpSpec" should s"verify InCorrect Code as UnAuthorized for User(${userId2}), Otp(${userId2Otp1}) (GET ${uri}/${userId2Otp1}/code/{code})" in {
      val codeUser = "123456"
      val request = Get(uri = s"${uri}/${userId2Otp1}/code/${codeUser}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[OtpCodeVerifyRes]
        rsp.code should ===(codeUser)
        rsp.authorized should ===(false)
      }
    }

  "OtpSpec" should s"be able to remove otps (DELETE ${uri})" in {
      val request = Delete(uri = s"${uri}/${testId}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
        //entityAs[String] should ===("""{"description":"Otp deleted."}""")
        val rsp = entityAs[OtpActionRes]
        rsp.status should startWith(s"""Success""")
      }
    }

  "OtpSpec" should s"return Otp after Delete ${userId2Otp1} (GET ${uri})" in {
      val request = Get(uri = s"${uri}/${userId2Otp1}")
      request ~> Route.seal(routes) ~> check {
        status should ===(StatusCodes.OK)
        contentType should ===(ContentTypes.`application/json`)
      }
    }

  "OtpSpec" should s"return 404 and NO Otp(${testId}) after Delete ${testId} (GET /otp)" in {
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
