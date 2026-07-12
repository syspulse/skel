package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Future, Promise, ExecutionContext}
import scala.concurrent.duration._

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest

import io.hacken.ext.wf._
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineActivity, EngineStatus}

/** Stub Engine: UUID id -> getRuntime (fixed run), workflowId -> getRuntimeByWorkflowId (latest run). */
class StubEngine extends Engine {
  val name = Engine.TEMPORAL
  private val acts = Seq(EngineActivity("a1", "ProofOfOwnership", EngineActivity.KIND_ACTIVITY, EngineStatus.COMPLETED))
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] =
    Future.successful(Some(EngineWorkflow(id = "PoR-Wf-1", runtimeId = runtimeId, name = "PoR-Flow", status = "RUNNING", namespace = "default", activities = acts)))
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] =
    Future.successful(Some(EngineWorkflow(id = workflowId, runtimeId = "run-xyz", name = "PoR-Flow", status = "RUNNING", namespace = "default", activities = acts)))
  def close(): Unit = ()
}

class AssemblyRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._

  val store = new WorkflowStoreMem()
  val typedSystem = ActorSystem(Behaviors.empty, "AsmTestSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  typedSystem.systemActorOf(Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry, Some(new StubEngine))(context))
    Behaviors.empty
  }, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = typedSystem.terminate()

  "POST /config/assembly" should {
    "assembly a WorkflowConfig from bracket DSL" in {
      Post("/config/assembly", WorkflowConfigDslReq("[A] -> [B] -> [C]")) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.graph.nodes should have size 3
        c.graph.links should have size 2
        c.xid shouldBe None
      }
    }
  }

  "POST /temporal/assembly/{id}" should {
    "assembly + link by runtimeId (UUID) -> name/meta.wid from WorkflowId, xid = the RunId" in {
      val RID = "019f51c0-3917-731b-864d-3b9d326db0aa"
      Post(s"/temporal/assembly/$RID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.name shouldBe "PoR-Wf-1"                       // WorkflowId from the resolved runtime
        c.xid shouldBe Some(RID)                         // RunId (the UUID we asked for)
        c.meta.flatMap(_.get("wid")).map(_.toString) shouldBe Some("PoR-Wf-1")
        c.graph.nodes should have size 2
      }
    }

    "assembly + link by workflowId -> name = WorkflowId, xid = latest RunId" in {
      val WID = "PoR-DefaultProject-1783782976365"
      Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.name shouldBe WID
        c.xid shouldBe Some("run-xyz")                   // latest RunId resolved by workflowId
        c.meta.flatMap(_.get("wid")).map(_.toString) shouldBe Some(WID)
      }
    }
  }

  "GET /config/resolve/{ids} (with Engine)" should {
    "return the WorkflowConfig with LIVE engine-mapped statuses" in {
      val WID = "PoR-Tracked-1"
      // link a config to the Temporal id
      Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
      }
      // resolve overlays the live runtime state onto config.status and each DetectorConfig.status
      Get(s"/config/resolve/$WID") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        r.configs.head.status shouldBe EngineStatus.RUNNING          // from the resolved runtime
        val dets = r.detectors.get.values.map(d => d.name -> d.status).toMap
        dets("ProofOfOwnership") shouldBe EngineStatus.COMPLETED      // matched activity -> live status
        dets("ProofOfReserve")   shouldBe EngineStatus.UNKNOWN        // no activity yet
      }
    }
  }
}
