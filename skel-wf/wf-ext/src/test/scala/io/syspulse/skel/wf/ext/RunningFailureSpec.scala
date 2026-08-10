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
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineActivity, EngineStatus}

/** Engine whose run is RUNNING but has a failing (retrying) task: the EngineTemporal folds a pending
 *  activity's lastFailure into status=RUNNING_FAILED + meta("err"). This stub simulates that result. */
class FailureEngine extends Engine {
  val name = Engine.ENGINE_TEMPORAL
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] =
    Future.successful(Some(EngineWorkflow(
      id = "PoR-Wf", runtimeId = runtimeId, name = "PoR-Flow", status = WorkflowStatus.RUNNING_FAILED,
      namespace = "default", meta = Map("err" -> "ProofOfOwnership: boom"),
      activities = Seq(EngineActivity("a1", "ProofOfOwnership", EngineActivity.KIND_ACTIVITY, EngineStatus.RUNNING, detail = Some("boom"))))))
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  def close(): Unit = ()
}

class RunningFailureSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._

  val store = new WorkflowStoreMem()
  val engine = Some(new FailureEngine)
  val typedSystem = ActorSystem(Behaviors.empty, "RunningFailureSystem")
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

  "GET /config/resolve/{id} for a RUNNING workflow with a failing task" should {
    "map the WorkflowConfig to RUNNING_FAILED and expose the error in meta.err" in {
      val RID = "019fcc06-ad08-7015-af47-a51a88a4b04a"
      val cfg = Post(s"/temporal/assembly/$RID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      Get(s"/config/resolve/$RID") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfigs].configs.head
        c.status shouldBe WorkflowStatus.RUNNING_FAILED
        c.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("ProofOfOwnership: boom")
      }
      // persisted status reflects the failure
      Await.result(store.getWConf(cfg.id), 5.seconds).status shouldBe WorkflowStatus.RUNNING_FAILED
    }
  }
}
