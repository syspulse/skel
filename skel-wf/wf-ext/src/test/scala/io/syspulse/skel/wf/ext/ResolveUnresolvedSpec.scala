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

import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineStatus}

/** Engine that has NO matching runtime (everything is obsolete / not present). */
class ObsoleteEngine extends Engine {
  val name = Engine.TEMPORAL
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  def close(): Unit = ()
}

class ResolveUnresolvedSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._

  val store = new WorkflowStoreMem()
  val engine = Some(new ObsoleteEngine)
  val typedSystem = ActorSystem(Behaviors.empty, "ResolveUnresolvedSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  typedSystem.systemActorOf(Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry, engine)(context)); Behaviors.empty
  }, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = typedSystem.terminate()

  "GET /config/resolve/{ids} when the runtime is obsolete on the Engine" should {
    "mark the WorkflowConfig AND all its DetectorConfigs as UNRESOLVED (never stale cached statuses)" in {
      val RID = "59ffd0ae-126e-4c70-aa52-b96585bfe1da"
      // assemble + link: engine can't resolve -> config is bound to the id (xid = RID) as fallback
      Post(s"/temporal/assembly/$RID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
      }
      Get(s"/config/resolve/$RID") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        r.configs.head.status shouldBe EngineStatus.UNRESOLVED
        val dets = r.detectors.get.values.toSeq
        dets should not be empty
        all (dets.map(_.status)) shouldBe EngineStatus.UNRESOLVED
      }
    }
  }
}
