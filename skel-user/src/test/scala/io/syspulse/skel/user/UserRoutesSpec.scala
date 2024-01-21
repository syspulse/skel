package io.syspulse.skel.user

import io.jvm.uuid._

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.syspulse.skel.user.User
import io.syspulse.skel.user.store._
import io.syspulse.skel.user.server._

import org.scalatest.compatible.Assertion
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.NotUsed
import org.scalatest.concurrent.ScalaFutures


class UserRoutesSpec extends AnyWordSpec with Matchers with ScalaFutures with ScalatestRouteTest {

  // the Akka HTTP route testkit does not yet support a typed actor system (https://github.com/akka/akka-http/issues/2036)
  // so we have to adapt for now
  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  override def createActorSystem(): akka.actor.ActorSystem = testKit.system.classicSystem

  def runWithContext[T,A](f: ActorContext[T] => A): A = {
    def extractor(replyTo: ActorRef[A]): Behavior[T] =
      Behaviors.setup { context =>
        replyTo ! f(context)

        Behaviors.ignore
      }
    val probe = testKit.createTestProbe[A]()
    testKit.spawn(extractor(probe.ref))
    probe.receiveMessage(FiniteDuration(1,TimeUnit.MINUTES))
  }

  // Here we need to implement all the abstract members of UserRoutes.
  // We use the real UserRegistryActor to test it while we hit the Routes,
  // but we could "mock" it by implementing it in-place or by using a TestProbe
  // created with testKit.createTestProbe()
  val userRegistry = testKit.spawn(UserRegistry())
  

  // use the json formats to marshal and unmarshall objects in the test
  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import UserJson._

  "UserRoutes" should runWithContext[NotUsed,Unit] { ctx => {

      val config = Config()
      val routes = new UserRoutes(userRegistry)(ctx,config).routes

      "return no users if no present (GET /user)" in  { 
        val request = HttpRequest(uri = "/user")

        request ~> routes ~> check {
          status should ===(StatusCodes.OK)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===("""{"users":[]}""")
        }
      }

      "be able to add users (POST /user)" in {
        val user = User(UUID.random)
        val userEntity = Marshal(user).to[MessageEntity].futureValue // futureValue is from ScalaFutures
        val request = Post("/user").withEntity(userEntity)

        request ~> routes ~> check {
          status should ===(StatusCodes.Created)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===("""{"description":"User Kapi created."}""")
        }
      }

      "be able to remove users (DELETE /user)" in {
        val request = Delete(uri = "/user/1")

        request ~> routes ~> check {
          status should ===(StatusCodes.OK)
          contentType should ===(ContentTypes.`application/json`)
          entityAs[String] should ===("""{"description":"User Kapi deleted."}""")
        }
      }

    }
  }
}
