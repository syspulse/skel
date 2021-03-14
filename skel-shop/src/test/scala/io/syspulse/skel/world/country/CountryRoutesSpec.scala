package io.syspulse.skel.world.country

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{ Matchers, WordSpec }

import io.jvm.uuid._

import io.syspulse.skel.world.country.CountryRegistry._

class CountryRoutesSpec extends WordSpec with Matchers with ScalaFutures with ScalatestRouteTest {
  
  // the Akka HTTP route testkit does not yet support a typed actor system (https://github.com/akka/akka-http/issues/2036)
  // so we have to adapt for now
  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem

  // Here we need to implement all the abstract members of CountryRoutes.
  // We use the real CountryRegistryActor to test it while we hit the Routes,
  // but we could "mock" it by implementing it in-place or by using a TestProbe
  // created with testKit.createTestProbe()
  val countryRegistry = testKit.spawn(CountryRegistry(new CountryStoreCache))
  lazy val routes = new CountryRoutes(countryRegistry).routes

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import CountryJson._
  
  // assumes tests are not run in parallel
  var testId:UUID = UUID.fromString("00000000-0000-0000-0000-000000000000")

  "CountryRoutes" should {
    "return no countrys if no present (GET /country)" in {
      val request = HttpRequest(uri = "/country")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        entityAs[String] should ===("""{"countrys":[]}""")
      }
    }

    "be able to add countrys (POST /country)" in {
      val countryCreate = CountryCreate("Canada", "CA")
      val countryEntity = Marshal(countryCreate).to[MessageEntity].futureValue 

      val request = Post("/country").withEntity(countryEntity)

      request ~> routes ~> check {
        status should ===(StatusCodes.Created)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[CountryActionPerformed]
        // country is created with random UUID !
        rsp.description should startWith(s"""created""")
        testId = rsp.id.get
      }
    }

    "return created country (GET /country)" in {
      val request = Get(uri = s"/country/${testId}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Country]
        rsp.id should ===(testId)
        rsp.name should ===("Canada")
        rsp.iso should ===("CA")
      }
    }

    "return 1 countrys (GET /country)" in {
      val request = Get(uri = s"/country/")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Countrys]

        rsp.countrys.size should ===(1)
      }
    }

    "be able to remove countrys (DELETE /country)" in {
      val request = Delete(uri = s"/country/${testId}")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        //entityAs[String] should ===("""{"description":"Country deleted."}""")
        val rsp = entityAs[CountryActionPerformed]
        rsp.description should startWith(s"""deleted""")
      }
    }

    "return all countrys for load (POST /country/load)" in {
      val request = Post(uri = "/country/load")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        entityAs[String] should !==("""{"countrys":[]}""")
      }
    }

    "return USA by 'US' (GET /country)" in {
      val request = Get(uri = s"/country/US")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Country]
        rsp.name should ===("United States")
        rsp.iso should ===("US")
      }
    }

    "return USA by 'United States' (GET /country)" in {
      val request = Get(uri = s"/country/United%20States")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)

        val rsp = entityAs[Country]
        rsp.name should ===("United States")
        rsp.iso should ===("US")
      }
    }

    "clear (DELETE /country)" in {
      val request = Delete(uri = "/country")

      request ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)
        val rsp = entityAs[ClearActionPerformed]
        rsp.description should startWith(s"""cleared""")

        //entityAs[String] should ===("""{"countrys":[]}""")
      }

      val request2 = Get(uri = "/country")
      request2 ~> routes ~> check {
        status should ===(StatusCodes.OK)

        contentType should ===(ContentTypes.`application/json`)
        entityAs[String] should ===("""{"countrys":[]}""")
      }
    }
  }
}
