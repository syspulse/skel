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
import io.syspulse.skel.Command
import io.syspulse.skel.otp.store._
import io.syspulse.skel.otp.server._
import io.syspulse.skel.config.Configuration
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterAll
import akka.actor.typed.Behavior
import akka.actor.typed.ActorRef
import io.syspulse.skel.service.Routeable

class ServerTestable(serviceUri:String, app:Seq[(Behavior[Command],String,(ActorRef[Command],ActorContext[_])=>Routeable)]) 
  extends HttpServiceTest with BeforeAndAfterAll {

  lazy val testKit = ActorTestKit()
  implicit def typedSystem = testKit.system
  val uri = serviceUri //"/api/v1/otp"
  @volatile var routes:Route = _

  override def beforeAll() = {
    println(s"Starting Server (ActorSystem)...")
    val server = new Server {
      val (rejectionHandler,exceptionHandler) = getHandlers()
      override def postInit(context:ActorContext[_],rr:Route) = {
        routes = rr
      }
    }

    val b = server.run( "localhost", 8081, uri, Configuration.default,
      app,
      Some(testKit.system)
    )  
    Thread.sleep(150L)
  }

  override def afterAll() = {
    println(s"Stopping Server (ActorSystem)...")
    Thread.sleep(250L)
    testKit.system.terminate()
  }

}
