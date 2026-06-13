package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest

import io.hacken.ext.wf._
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._

class WorkflowRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowGrafJson._

  val store = new WorkflowStoreMem()
  val typedSystem = ActorSystem(Behaviors.empty, "WfTestSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  val testBehavior = Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry)(context))
    Behaviors.empty
  }
  typedSystem.systemActorOf(testBehavior, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = typedSystem.terminate()

  "WorkflowRoutes" should {

    "return empty schema list" in {
      Get("/schema") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowSchemas].total shouldBe 0L
      }
    }

    "create a WorkflowSchema via DSL and read it back" in {
      Post("/schema/dsl", WorkflowSchemaDslReq("Detector.a -> Detector.b -> Detector.c", wid = Some(0), name = Some("WAudit"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowSchema].name shouldBe "WAudit"
      }
      Get("/schema/0") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val v = responseAs[WorkflowSchemaView]
        v.schema.graph.nodes should have size 3
        v.detectors shouldBe None // ?detector defaults to id
      }
    }

    "expand detectors with ?detector=full" in {
      Get("/schema/0?detector=full") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val v = responseAs[WorkflowSchemaView]
        v.detectors.isDefined shouldBe true
        v.detectors.get should have size 3
      }
    }

    "reject paging with only one of from/size" in {
      Get("/schema?from=0") ~> routes.routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "assemble a WorkflowConfig via DSL and read it back with ?detector=full" in {
      Post("/config/dsl", WorkflowConfigDslReq("Detector.x -> Detector.y", name = Some("WFlow"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfig].graph.isInstance shouldBe true
      }
      Get("/config") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].total shouldBe 1L
      }
      Get("/config/0?detector=full") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigView].detectors.get should have size 2
      }
    }

    "create and fetch a WorkflowGraf" in {
      val g = WorkflowGraf(id = 0).withNode(WorkflowNode(id = 0, title = "n", sid = 1))
      val created = Post("/graf", WorkflowGrafCreateReq(graph = Some(g))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowGraf]
      }
      created.nodes should have size 1
      Get(s"/graf/${created.id}") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowGraf].nodes should have size 1
      }
      // the DSL endpoints also persist grafs, so just assert ours is present
      Get("/graf") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowGrafs].grafs.map(_.id) should contain(created.id)
      }
    }

    "delete a schema" in {
      Delete("/schema/0") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].status shouldBe WorkflowActionRes.OK
      }
    }
  }
}
