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
  val engine = new StartingEngine
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
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("StartingFlow"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }

      val started = Post(s"/schema/${sc.id}/start") ~~> routes.routes ~> check {
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
      Await.result(store.getWConf(started.id), 5.seconds).status shouldBe WorkflowStatus.STARTING
      val cids = started.graph.nodes.values.flatMap(_.cid).toSeq
      cids should not be empty
      cids.foreach { cid =>
        Await.result(store.getDConf(cid), 5.seconds).get.status shouldBe WorkflowStatus.STARTING
      }
    }
  }

  "POST /config/{id}/start (start an existing WorkflowConfig)" should {
    "start an UNKNOWN or FAILED + no-xid config, and reject one already started or in another state" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("SaveThenStart"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }

      // [Save]: create a NOT-started config (status UNKNOWN, no xid) via the POST /config overlay
      val saved = Post("/config", WorkflowConfigCreateReq(sid = sc.id, name = Some("saved-1"), status = Some(WorkflowStatus.UNKNOWN))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.status shouldBe WorkflowStatus.UNKNOWN
        c.xid shouldBe None
        c
      }

      // [Start]: an UNKNOWN + no-xid config starts -> xid bound (persisted). (The mock Engine's
      // getRuntime returns None, so the resolved status is engine-dependent; the reliable "started"
      // signal is the bound runtime id.)
      val started = Post(s"/config/${saved.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.xid shouldBe Some("run-new-1")            // start succeeded -> runtime id bound
        c
      }
      Await.result(store.getWConf(started.id), 5.seconds).xid shouldBe Some("run-new-1")

      // rejected: already started (xid present)
      Post(s"/config/${started.id}/start") ~~> routes.routes ~> check {
        status should not be StatusCodes.OK
      }

      // [Start]: FAILED + no xid (previous Engine start failed) is startable
      val failed = Post("/config", WorkflowConfigCreateReq(sid = sc.id, name = Some("failed-1"), status = Some(WorkflowStatus.FAILED))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.status shouldBe WorkflowStatus.FAILED
        c.xid shouldBe None
        c
      }
      Post(s"/config/${failed.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfig].xid shouldBe Some("run-new-1")
      }

      // rejected: a config in another state (ACTIVE, no xid) is not startable
      val active = Post("/config", WorkflowConfigCreateReq(sid = sc.id, name = Some("active-1"), status = Some(WorkflowStatus.ACTIVE))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      active.status shouldBe WorkflowStatus.ACTIVE
      Post(s"/config/${active.id}/start") ~~> routes.routes ~> check {
        status should not be StatusCodes.OK
      }
    }
  }
}

/** Engine whose start() fails: the created/saved WorkflowConfig is returned as FAILED (HTTP 200). */
class FailingStartEngine(var failMsg: String = "engine start boom") extends Engine {
  val name = Engine.ENGINE_TEMPORAL
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  override def start(ns: Option[String], workflowType: String, workflowId: String, taskQueue: String, input: Option[String], memo: Map[String, String] = Map.empty): Future[EngineStart] =
    Future.failed(new Exception(failMsg))
  def close(): Unit = ()
}

class WorkflowStartFailSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._

  val store = new WorkflowStoreMem()
  val engine = new FailingStartEngine
  val typedSystem = ActorSystem(Behaviors.empty, "StartFailTestSystem")
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

  "POST /schema/{id}/start when Engine.start fails" should {
    "return 200 with the created WorkflowConfig as FAILED and meta.err" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("FailStart"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }

      val created = Post(s"/schema/${sc.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        val c = r.configs.head
        c.sid shouldBe sc.id
        c.status shouldBe WorkflowStatus.FAILED
        c.xid shouldBe None
        c.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("engine start boom")
        c
      }

      val stored = Await.result(store.getWConf(created.id), 5.seconds)
      stored.status shouldBe WorkflowStatus.FAILED
      stored.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("engine start boom")
    }
  }

  "POST /config/{id}/start when Engine.start fails" should {
    "return 200 with the WorkflowConfig as FAILED and meta.err" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("FailStartSaved"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val saved = Post("/config", WorkflowConfigCreateReq(sid = sc.id, name = Some("saved-fail"), status = Some(WorkflowStatus.UNKNOWN))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      saved.status shouldBe WorkflowStatus.UNKNOWN

      Post(s"/config/${saved.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.id shouldBe saved.id
        c.status shouldBe WorkflowStatus.FAILED
        c.xid shouldBe None
        c.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("engine start boom")
      }

      val failed = Await.result(store.getWConf(saved.id), 5.seconds)
      failed.status shouldBe WorkflowStatus.FAILED
      failed.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("engine start boom")
    }

    "update meta.err when the same FAILED config is started again and Engine.start fails again" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("FailStartRetry"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val saved = Post("/config", WorkflowConfigCreateReq(sid = sc.id, name = Some("saved-fail-retry"), status = Some(WorkflowStatus.UNKNOWN))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }

      Post(s"/config/${saved.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.status shouldBe WorkflowStatus.FAILED
        c.xid shouldBe None
        c.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("engine start boom")
      }

      engine.failMsg = "engine start boom 2"
      Post(s"/config/${saved.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.id shouldBe saved.id
        c.status shouldBe WorkflowStatus.FAILED
        c.xid shouldBe None
        c.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("engine start boom 2")
        c.meta.get.keySet should contain ("err")
      }

      val stored = Await.result(store.getWConf(saved.id), 5.seconds)
      stored.status shouldBe WorkflowStatus.FAILED
      stored.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("engine start boom 2")
    }
  }
}

/** Engine whose start SUCCEEDS but the new run is already FAILED. `errMeta` is whatever the
 *  caller injects — empty simulates EngineTemporal not copying WORKFLOW_EXECUTION_FAILED. */
class ImmediateFailedEngine(errMeta: Map[String, String] = Map.empty) extends Engine {
  val name = Engine.ENGINE_TEMPORAL
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] =
    Future.successful(Some(EngineWorkflow(
      id = "Demo-Fail", runtimeId = runtimeId, name = "Demo", status = WorkflowStatus.FAILED,
      namespace = "default", meta = errMeta)))
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] = Future.successful(None)
  override def start(ns: Option[String], workflowType: String, workflowId: String, taskQueue: String, input: Option[String], memo: Map[String, String] = Map.empty): Future[EngineStart] =
    Future.successful(EngineStart(workflowId, "run-failed-1", ns.getOrElse("default")))
  def close(): Unit = ()
}

class WorkflowStartImmediateFailSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._

  val store = new WorkflowStoreMem()
  val engine = new ImmediateFailedEngine(Map("err" -> "activity timed out"))
  val typedSystem = ActorSystem(Behaviors.empty, "StartImmediateFailSystem")
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

  "POST /config/{id}/start when the new run is already FAILED" should {
    "write meta.err from the Engine even if status stays FAILED" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("ImmediateFail"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val saved = Post("/config", WorkflowConfigCreateReq(sid = sc.id, name = Some("failed-again"), status = Some(WorkflowStatus.FAILED))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      saved.status shouldBe WorkflowStatus.FAILED
      saved.meta.flatMap(_.get("err")) shouldBe None

      Post(s"/config/${saved.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.status shouldBe WorkflowStatus.FAILED
        c.xid shouldBe Some("run-failed-1")
        c.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("activity timed out")
      }

      val stored = Await.result(store.getWConf(saved.id), 5.seconds)
      stored.status shouldBe WorkflowStatus.FAILED
      stored.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("activity timed out")
    }
  }
}

class WorkflowStartKeepErrSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._

  val store = new WorkflowStoreMem()
  // Engine reports FAILED with no err (the pre-fix EngineTemporal behavior)
  val engine = new ImmediateFailedEngine(Map.empty)
  val typedSystem = ActorSystem(Behaviors.empty, "StartKeepErrSystem")
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

  "POST /config/{id}/start when Engine reports FAILED with no err" should {
    "keep the previously stored meta.err (status stays FAILED)" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.A -> Detector.B", name = Some("KeepErr"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val saved = Post("/config", WorkflowConfigCreateReq(
        sid = sc.id, name = Some("failed-keep-err"), status = Some(WorkflowStatus.FAILED),
        meta = Some(Map("err" -> "old start boom")))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      saved.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("old start boom")

      Post(s"/config/${saved.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfig]
        c.status shouldBe WorkflowStatus.FAILED
        c.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("old start boom")
        c.meta.get.keySet should contain ("err")
      }

      val stored = Await.result(store.getWConf(saved.id), 5.seconds)
      stored.meta.flatMap(_.get("err")).map(_.toString) shouldBe Some("old start boom")
    }
  }
}
