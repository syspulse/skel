package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest

import io.hacken.ext.wf.WorkflowConfig
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineActivity, EngineStatus}

/**
 * Engine where the SPECIFIC run (by RunId) is gone, but the WorkflowId still has a newer, RUNNING
 * run. Resolving by the (obsolete) RunId must NOT report the latest run - it must be UNRESOLVED.
 */
class StaleRunEngine extends Engine {
  val name = Engine.ENGINE_TEMPORAL
  private val acts = Seq(EngineActivity("a1", "a", EngineActivity.KIND_ACTIVITY, EngineStatus.COMPLETED))
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] =
    Future.successful(None) // the exact run does NOT exist on the engine
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] =
    Future.successful(Some(EngineWorkflow(id = workflowId, runtimeId = "run-new", name = "Flow", status = "RUNNING", namespace = "default", activities = acts)))
  def close(): Unit = ()
}

class ResolveStaleRunSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._

  val store = new WorkflowStoreMem()
  val engine = new StaleRunEngine
  val typedSystem = ActorSystem(Behaviors.empty, "ResolveStaleRunSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  typedSystem.systemActorOf(Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry, engine)(context, config)); Behaviors.empty
  }, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = {
    typedSystem.terminate()
    Await.result(typedSystem.whenTerminated, 10.seconds)
    super.afterAll() // shut down the ScalatestRouteTest actor system too (else it leaks after the suite)
  }

  "GET /config/resolve/{runId} for an obsolete run whose WorkflowId still has a live run" should {
    "resolve by the EXACT RunId and return UNRESOLVED (NOT the latest run's RUNNING)" in {
      val RID = "59ffd0ae-126e-4c70-aa52-b96585bfe1da"   // obsolete RunId
      val WID = "PoR-DefaultProject-1783782976365"       // WorkflowId with a newer RUNNING run
      val c = Post("/config/dsl", WorkflowConfigDslReq("Detector.a -> Detector.b", name = Some("R1"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      // bind to the obsolete run, but ALSO carry meta.wid (as a workflowId-bound config would)
      Await.result(store.addWConf(c.copy(xid = Some(RID), meta = Some(Map("wid" -> WID)))), 5.seconds)

      Get(s"/config/resolve/$RID") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        r.configs.head.status shouldBe EngineStatus.UNRESOLVED         // NOT RUNNING
        all (r.detectors.get.values.map(_.status).toSeq) shouldBe EngineStatus.UNRESOLVED
      }
    }
  }
}
