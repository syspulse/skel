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

import io.hacken.ext.wf._
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineStart}

/** Engine whose start SUCCEEDS but whose new run is not yet visible (getRuntime -> None): the
 *  classic visibility-lag window right after a start. */
class StartingEngine extends Engine {
  val name = Engine.ENGINE_TEMPORAL
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  override def start(ns: Option[String], workflowType: String, workflowId: String, taskQueue: String, input: Option[String], memo: Map[String, String] = Map.empty): Future[EngineStart] =
    Future.successful(EngineStart(workflowId, "run-new-1", ns.getOrElse("default")))
  def close(): Unit = ()
}

class WorkflowStartSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._

  val store = new WorkflowStoreMem()
  val engine = Some(new StartingEngine)
  val typedSystem = ActorSystem(Behaviors.empty, "StartTestSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  typedSystem.systemActorOf(Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry, engine)(context, config)); Behaviors.empty
  }, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = {
    typedSystem.terminate()
    Await.result(typedSystem.whenTerminated, 10.seconds)
    super.afterAll()
  }

  "POST /schema/{id}/start when the run is not yet visible on the Engine" should {
    "present (and persist) the created WorkflowConfig + DetectorConfigs as STARTING (not UNRESOLVED)" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("StartingFlow"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }

      val started = Post(s"/schema/${sc.id}/start") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        val c = r.configs.head
        c.sid shouldBe sc.id                                   // a NEW config created from the schema
        c.xid shouldBe Some("run-new-1")                      // start succeeded -> xid bound
        c.status shouldBe WorkflowStatus.STARTING             // not UNRESOLVED (we know it just started)
        all (r.detectors.get.values.map(_.status).toSeq) shouldBe WorkflowStatus.STARTING
        c
      }

      // STARTING is persisted for the config and its detectors
      Await.result(store.getConfig(started.id), 5.seconds).status shouldBe WorkflowStatus.STARTING
      val cids = started.graph.nodes.values.flatMap(_.cid).toSeq
      cids should not be empty
      cids.foreach { cid =>
        Await.result(store.getDetectorConfig(cid), 5.seconds).get.status shouldBe WorkflowStatus.STARTING
      }
    }
  }
}
