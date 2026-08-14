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
import io.syspulse.skel.wf.ext.engine.{Engine, EngineWorkflow, EngineActivity, EngineStatus, EngineStart}

/** Stub Engine: UUID id -> getRuntime (fixed run), workflowId -> getRuntimeByWorkflowId (latest run). */
class StubEngine extends Engine {
  val name = Engine.ENGINE_TEMPORAL
  private val acts = Seq(EngineActivity("a1", "ProofOfOwnership", EngineActivity.KIND_ACTIVITY, EngineStatus.COMPLETED))
  // records the last start() call so tests can assert what the API sent to the Engine
  @volatile var lastStart: Option[(String, String, String, Option[String])] = None // (type, wid, taskQueue, input)
  @volatile var lastRunId: String = "" // the RunId returned by the most recent start() (unique per call)
  private val runCounter = new java.util.concurrent.atomic.AtomicInteger(0)
  def namespaces(): Future[Seq[String]] = Future.successful(Seq("default"))
  def getRuntimes(ns: Option[String], pageSize: Int): Future[Seq[EngineWorkflow]] = Future.successful(Seq())
  def getRuntime(ns: Option[String], runtimeId: String): Future[Option[EngineWorkflow]] =
    Future.successful(Some(EngineWorkflow(id = "PoR-Wf-1", runtimeId = runtimeId, name = "PoR-Flow", status = "RUNNING", namespace = "default", activities = acts)))
  def getRuntimeByWorkflowId(ns: Option[String], workflowId: String): Future[Option[EngineWorkflow]] =
    Future.successful(Some(EngineWorkflow(id = workflowId, runtimeId = "run-xyz", name = "PoR-Flow", status = "RUNNING", namespace = "default", activities = acts)))
  override def start(ns: Option[String], workflowType: String, workflowId: String, taskQueue: String, input: Option[String], memo: Map[String, String] = Map.empty): Future[EngineStart] = {
    lastStart = Some((workflowType, workflowId, taskQueue, input))
    lastRunId = s"run-started-${runCounter.incrementAndGet()}" // unique per start -> unique xid (avoids RID resolve collisions)
    Future.successful(EngineStart(workflowId, lastRunId, ns.getOrElse("default")))
  }
  @volatile var lastTerminate: Option[(String, Option[String], Option[String])] = None // (workflowId, runId, reason)
  @volatile var lastCancel: Option[(String, Option[String], Option[String])] = None
  override def terminate(ns: Option[String], workflowId: String, runId: Option[String], reason: Option[String]): Future[Unit] = {
    lastTerminate = Some((workflowId, runId, reason)); Future.successful(())
  }
  override def cancel(ns: Option[String], workflowId: String, runId: Option[String], reason: Option[String]): Future[Unit] = {
    lastCancel = Some((workflowId, runId, reason)); Future.successful(())
  }
  def close(): Unit = ()
}

class AssemblyRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._

  val store = new WorkflowStoreMem()
  val stubEngine = new StubEngine
  val engine = Some(stubEngine)
  val typedSystem = ActorSystem(Behaviors.empty, "AsmTestSystem")
  val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine), "WorkflowRegistry")

  val routesPromise = Promise[WorkflowRoutes]()
  typedSystem.systemActorOf(Behaviors.setup[Any] { context =>
    routesPromise.success(new WorkflowRoutes(registry, engine)(context, config))
    Behaviors.empty
  }, "test-actor")
  val routes = Await.result(routesPromise.future, 5.seconds)

  override def afterAll(): Unit = {
    typedSystem.terminate()
    Await.result(typedSystem.whenTerminated, 10.seconds)
    super.afterAll() // shut down the ScalatestRouteTest actor system too (else it leaks after the suite)
  }

  "POST /config/assembly" should {
    "assembly a WorkflowConfig from bracket DSL" in {
      Post("/config/assembly", WorkflowConfigDslReq("[A] -> [B] -> [C]")) ~~> routes.routes ~> check {
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
      Post(s"/temporal/assembly/$RID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~~> routes.routes ~> check {
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
      Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~~> routes.routes ~> check {
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
      Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
      }
      // resolve overlays the live runtime state onto config.status and each DetectorConfig.status
      Get(s"/config/resolve/$WID") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        r.configs.head.status shouldBe EngineStatus.RUNNING          // from the resolved runtime
        val dets = r.detectors.get.values.map(d => d.name -> d.status).toMap
        dets("ProofOfOwnership") shouldBe EngineStatus.COMPLETED      // matched activity -> live status
        dets("ProofOfReserve")   shouldBe EngineStatus.UNKNOWN        // no activity yet
        // the matched activity's id is exposed via DetectorConfig.meta.activity_id
        val byName = r.detectors.get.values.map(d => d.name -> d).toMap
        byName("ProofOfOwnership").meta.flatMap(_.get("activity_id")) shouldBe Some("a1")
        byName("ProofOfReserve").meta.flatMap(_.get("activity_id")) shouldBe None
      }
    }

    "resolve by WorkflowConfig.id (type=id) -> match by numeric id, query the Engine by that config's xid" in {
      val WID = "PoR-ById-1"
      val cfg = Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      Get(s"/config/resolve/${cfg.id}?type=id") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        r.configs.head.id shouldBe cfg.id
        r.configs.head.status shouldBe EngineStatus.RUNNING   // matched by id -> resolved via xid
      }
    }

    "PERSIST the Engine statuses back to the store (Mem: WorkflowConfig + DetectorConfig)" in {
      val WID = "PoR-Persist-1"
      val cfg = Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership] -> [ProofOfReserve]")) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      // assembled statuses are ACTIVE (not yet resolved)
      Await.result(store.getWConf(cfg.id), 5.seconds).status shouldBe "ACTIVE"
      val pooCid = cfg.graph.nodes.values.find(_.title == "ProofOfOwnership").flatMap(_.cid).get
      val porCid = cfg.graph.nodes.values.find(_.title == "ProofOfReserve").flatMap(_.cid).get
      Await.result(store.getDConf(pooCid), 5.seconds).get.status shouldBe "ACTIVE"

      Get(s"/config/resolve/$WID") ~~> routes.routes ~> check { status shouldBe StatusCodes.OK }

      // the store now reflects the Engine truth (WorkflowConfig + DetectorConfig)
      Await.result(store.getWConf(cfg.id), 5.seconds).status shouldBe EngineStatus.RUNNING
      Await.result(store.getDConf(pooCid), 5.seconds).get.status shouldBe EngineStatus.COMPLETED // matched activity
      Await.result(store.getDConf(porCid), 5.seconds).get.status shouldBe EngineStatus.UNKNOWN   // no activity yet
    }

    "POST /schema/{id}/start creates a WorkflowConfig from the schema and starts an Engine execution (xid + meta.wid, resolved)" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.ProofOfOwnership -> Detector.ProofOfReserve", name = Some("StartFlow"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }

      val started = Post(s"/schema/${sc.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        val c = r.configs.head
        c.sid shouldBe sc.id                                    // a NEW config created from the schema
        c.xid shouldBe Some(stubEngine.lastRunId)                   // xid = the started RunId
        c.status shouldBe EngineStatus.RUNNING                 // live status pulled by Resolve
        c.meta.flatMap(_.get("wid")).map(_.toString).isDefined shouldBe true
        c
      }

      // the binding is PERSISTED
      val saved = Await.result(store.getWConf(started.id), 5.seconds)
      saved.xid shouldBe Some(stubEngine.lastRunId)

      // Engine received: WorkflowType == schema.name; WorkflowId == config.title (or .name if title empty)
      val (wtype, wid, tq, input) = stubEngine.lastStart.get
      wtype shouldBe sc.name
      wid shouldBe (if (started.title.trim.nonEmpty) started.title else started.name)
      tq shouldBe "GENERIC_WORKFLOW_QUEUE"
      input.isDefined shouldBe true
    }

    "POST /schema/{id}/start honors ?tq and a caller-supplied JSON input body" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.ProofOfOwnership", name = Some("StartFlow2"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      Post(s"/schema/${sc.id}/start?tq=MY_QUEUE", WorkflowSchemaStartReq(input = Some(spray.json.JsObject("k" -> spray.json.JsString("v"))))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].configs.head.xid shouldBe Some(stubEngine.lastRunId)
      }
      val (_, _, tq, input) = stubEngine.lastStart.get
      tq shouldBe "MY_QUEUE"                                    // request overrides the default
      input shouldBe Some("""{"k":"v"}""")                     // caller input overrides the config payload
    }

    "POST /schema/{id}/start {input,config} replaces WorkflowConfig.config and still forwards input" in {
      val sc = Post("/schema", WorkflowSchemaCreateReq(
        name = "StartCfg",
        schema = Some(spray.json.JsObject(
          "type" -> spray.json.JsString("object"),
          "properties" -> spray.json.JsObject(
            "severity" -> spray.json.JsObject("type" -> spray.json.JsString("number"), "default" -> spray.json.JsNumber(0.5))
          )
        ))
      )) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val started = Post(s"/schema/${sc.id}/start", WorkflowSchemaStartReq(
        input  = Some(spray.json.JsObject("k" -> spray.json.JsString("v"))),
        config = Some(spray.json.JsObject("severity" -> spray.json.JsNumber(0.9), "note" -> spray.json.JsString("custom"))),
      )) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfigs].configs.head
        c.config.flatMap(_.fields.get("severity")) shouldBe Some(spray.json.JsNumber(0.9))
        c.config.flatMap(_.fields.get("note")) shouldBe Some(spray.json.JsString("custom"))
        c
      }
      val saved = Await.result(store.getWConf(started.id), 5.seconds)
      saved.config.flatMap(_.fields.get("severity")) shouldBe Some(spray.json.JsNumber(0.9))
      val (_, _, _, input) = stubEngine.lastStart.get
      input shouldBe Some("""{"k":"v"}""")
    }

    "POST /schema/{id}/start uses WorkflowSchema.meta.input when the body omits input" in {
      val sc = Post("/schema", WorkflowSchemaCreateReq(name = "StartMetaIn")) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      Put(s"/schema/${sc.id}", WorkflowSchemaUpdateReq(meta = Some(Map("input" -> """{"from":"schema"}""")))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowSchema].meta.flatMap(_.get("input")) shouldBe Some("""{"from":"schema"}""")
      }
      val started = Post(s"/schema/${sc.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val c = responseAs[WorkflowConfigs].configs.head
        c.meta.flatMap(_.get("input")) shouldBe Some("""{"from":"schema"}""")
        c
      }
      val (_, _, _, input) = stubEngine.lastStart.get
      input shouldBe Some("""{"from":"schema"}""")
      Await.result(store.getWConf(started.id), 5.seconds).meta.flatMap(_.get("input")) shouldBe Some("""{"from":"schema"}""")
    }

    "POST /schema/{id}/start without config keeps the JsonSchema default WorkflowConfig.config" in {
      val sc = Post("/schema", WorkflowSchemaCreateReq(
        name = "StartDef",
        schema = Some(spray.json.JsObject(
          "type" -> spray.json.JsString("object"),
          "properties" -> spray.json.JsObject(
            "severity" -> spray.json.JsObject("type" -> spray.json.JsString("number"), "default" -> spray.json.JsNumber(0.5))
          )
        ))
      )) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val started = Post(s"/schema/${sc.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].configs.head
      }
      started.config.flatMap(_.fields.get("severity")) shouldBe Some(spray.json.JsNumber(0.5))
    }

    "WorkflowConfig.from substitutes {id}/{ts}/{meta} placeholders in name and title" in {
      val sc = WorkflowSchema.of(0, "Type-{id}", WorkflowGraf(id = 0))
        .copy(title = "run-{pid}-{id}", meta = Some(Map("pid" -> "P7")))
      val c = WorkflowConfig.from(7, sc)
      c.name shouldBe "Type-7"                                  // {id} -> new config id
      c.title shouldBe "run-P7-7"                               // {pid} -> meta.pid, {id} -> config id
      c.title should not include "{"
      // {ts} resolves to a numeric epoch (unknown keys drop to "")
      WorkflowConfig.from(9, sc.copy(title = "t-{ts}-{nope}")).title should fullyMatch regex "t-[0-9]+-"
    }

    "POST /schema/{id}/start substitutes {id}/{ts} and derives a unique WorkflowId" in {
      val sc = Post("/schema", WorkflowSchemaCreateReq(name = "Type-{id}", title = Some("run-{id}-{ts}"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val c = Post(s"/schema/${sc.id}/start") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfigs].configs.head
      }
      c.name shouldBe s"Type-${c.id}"                           // WorkflowType == substituted schema.name
      c.title should startWith (s"run-${c.id}-")
      c.title should not include "{"
      val (wtype, wid, _, _) = stubEngine.lastStart.get
      wtype shouldBe c.name
      wid shouldBe c.title                                      // WorkflowId == substituted title (unique)
    }

    "POST /schema/{id}/start?wid=... overrides the WorkflowId" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.ProofOfOwnership", name = Some("StartFlow4"))) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      Post(s"/schema/${sc.id}/start?wid=custom-wid-123") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].configs.head.xid shouldBe Some(stubEngine.lastRunId)
      }
      val (_, wid, _, _) = stubEngine.lastStart.get
      wid shouldBe "custom-wid-123"
    }

    "POST /config/{id}/stop terminates the Engine workflow (workflowId+xid, reason) and sets TERMINATED" in {
      val WID = "PoR-Stop-1"
      val cfg = Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership]")) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      Post(s"/config/${cfg.id}/stop?reason=done") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfig].status shouldBe WorkflowStatus.TERMINATED
      }
      Await.result(store.getWConf(cfg.id), 5.seconds).status shouldBe WorkflowStatus.TERMINATED
      val (wid, runId, reason) = stubEngine.lastTerminate.get
      wid shouldBe WID                 // workflowId = meta.wid
      runId shouldBe cfg.xid           // runId = the config's xid
      reason shouldBe Some("done")
    }

    "POST /config/{id}/cancel request-cancels the Engine workflow and sets CANCELED" in {
      val WID = "PoR-Cancel-1"
      val cfg = Post(s"/temporal/assembly/$WID", WorkflowConfigDslReq("[ProofOfOwnership]")) ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      Post(s"/config/${cfg.id}/cancel") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfig].status shouldBe WorkflowStatus.CANCELED
      }
      Await.result(store.getWConf(cfg.id), 5.seconds).status shouldBe WorkflowStatus.CANCELED
      val (wid, runId, reason) = stubEngine.lastCancel.get
      wid shouldBe WID
      runId shouldBe cfg.xid
      reason shouldBe None             // no reason passed
    }
  }
}
