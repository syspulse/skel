package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.{StatusCodes, ContentTypes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest

import io.hacken.ext.detector.{DetectorSchema, DetectorConfig}
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._

class DetectorRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.detector.DetectorSchemaJson._
  import io.hacken.ext.detector.DetectorConfigJson._

  val store = new WorkflowStoreMem()
  val typedSystem = ActorSystem(Behaviors.empty, "DetTestSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  typedSystem.systemActorOf(Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry)(context, config)); Behaviors.empty
  }, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  // errors are rendered as JSON by the Server-level handlers (WorkflowRoutes just completes). Seal
  // the route with the same handlers so error responses reflect production.
  private val (rejectionHandler, exceptionHandler) = (new io.syspulse.skel.Server {}).getHandlers()
  val apiRoutes = handleRejections(rejectionHandler) { handleExceptions(exceptionHandler) { routes.routes } }

  override def afterAll(): Unit = {
    typedSystem.terminate()
    Await.result(typedSystem.whenTerminated, 10.seconds)
    super.afterAll() // shut down the ScalatestRouteTest actor system too (else it leaks after the suite)
  }

  "Detector REST API" should {

    "start empty" in {
      Get("/detector/schema") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[DetectorSchemas].total shouldBe 0L
      }
      Get("/detector/config") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[DetectorConfigs].total shouldBe 0L
      }
    }

    "create a DetectorSchema and read it back" in {
      val created = Post("/detector/schema", DetectorSchemaCreateReq(name = "Scanner", title = Some("Scanner Detector"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[DetectorSchema]
      }
      created.id shouldBe 0
      created.name shouldBe "Scanner"
      created.title shouldBe "Scanner Detector"

      Get("/detector/schema/0") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[DetectorSchema].name shouldBe "Scanner"
      }
    }

    "update DetectorSchema keeps schema and uiSchema (Mem)" in {
      import spray.json._
      val sch = JsObject("type" -> JsString("object"), "properties" -> JsObject(
        "severity" -> JsObject("type" -> JsString("number"), "default" -> JsNumber(0.5))))
      val ui = JsObject("ui:order" -> JsArray(JsString("severity")))
      Put("/detector/schema/0", DetectorSchemaUpdateReq(schema = Some(sch), uiSchema = Some(ui))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val s = responseAs[DetectorSchema]
        s.schema shouldBe Some(sch)
        s.uiSchema shouldBe Some(ui)
      }
      Put("/detector/schema/0", DetectorSchemaUpdateReq(name = Some("Scanner2"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val s = responseAs[DetectorSchema]
        s.name shouldBe "Scanner2"
        s.schema shouldBe Some(sch)
        s.uiSchema shouldBe Some(ui)
      }
    }

    "create MULTIPLE DetectorConfigs of the SAME DetectorSchema (1 schema -> many configs)" in {
      val c1 = Post("/detector/config", DetectorConfigCreateReq(name = "scan-eth", sid = Some(0))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[DetectorConfig]
      }
      val c2 = Post("/detector/config", DetectorConfigCreateReq(name = "scan-btc", sid = Some(0))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[DetectorConfig]
      }
      c1.id should not be c2.id
      c1.schema.map(_.id) shouldBe Some(0)
      c2.schema.map(_.id) shouldBe Some(0) // both reference DetectorSchema 0

      Get("/detector/config") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[DetectorConfigs].total shouldBe 2L
      }
    }

    "update a DetectorConfig" in {
      Put("/detector/config/0", DetectorConfigUpdateReq(status = Some("DISABLED"), source = Some("eth-mainnet"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[DetectorConfig]
        c.status shouldBe "DISABLED"
        c.source shouldBe "eth-mainnet"
        // nested DetectorSchema.schema/uiSchema copied at create must survive the update
        c.schema.flatMap(_.schema).isDefined shouldBe true
        c.schema.flatMap(_.uiSchema).isDefined shouldBe true
      }
    }

    "delete a DetectorConfig and a DetectorSchema" in {
      Delete("/detector/config/1") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].status shouldBe WorkflowActionRes.OK
      }
      Delete("/detector/schema/0") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].status shouldBe WorkflowActionRes.OK
      }
    }

    "404 with a JSON error body on missing detector (via Server handlers)" in {
      Get("/detector/schema/999") ~~> apiRoutes ~> check {
        status shouldBe StatusCodes.NotFound
        contentType shouldBe ContentTypes.`application/json`
        val body = responseAs[String]
        body should include ("\"error\"")
        body should include ("\"code\":40104") // Err.NOT_FOUND
      }
    }
  }
}
