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
import io.hacken.ext.detector.DetectorConfig
import io.syspulse.skel.wf.ext.store.{WorkflowStoreMem, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._

class WorkflowRoutesSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowSchemaJson._
  import io.hacken.ext.wf.WorkflowConfigJson._
  import io.hacken.ext.wf.WorkflowGrafJson._
  import io.hacken.ext.detector.DetectorConfigJson._

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

  override def afterAll(): Unit = {
    typedSystem.terminate()
    Await.result(typedSystem.whenTerminated, 10.seconds)
    super.afterAll() // shut down the ScalatestRouteTest actor system too (else it leaks after the suite)
  }

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
        v.schema.graph.nodes should have size 3 // ?entity defaults to graf -> graph kept
        v.detectors shouldBe None
      }
    }

    "select response sections with ?entity CSV (graf default; schema adds DetectorSchema)" in {
      // schema -> DetectorSchema map; graf NOT requested -> graph is stripped
      Get("/schema/0?entity=schema") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val v = responseAs[WorkflowSchemaView]
        v.detectors.get should have size 3
        v.schema.graph.nodes shouldBe empty
      }
      // graf,schema -> both the graph and the DetectorSchema map
      Get("/schema/0?entity=graf,schema") ~> routes.routes ~> check {
        val v = responseAs[WorkflowSchemaView]
        v.detectors.get should have size 3
        v.schema.graph.nodes should have size 3
      }
      // all == graf,detector,schema (a schema has no DetectorConfig, so `detector` is a no-op here)
      Get("/schema/0?entity=all") ~> routes.routes ~> check {
        val v = responseAs[WorkflowSchemaView]
        v.detectors.get should have size 3
        v.schema.graph.nodes should have size 3
      }
      // detector alone -> no DetectorSchema map, graf stripped
      Get("/schema/0?entity=detector") ~> routes.routes ~> check {
        val v = responseAs[WorkflowSchemaView]
        v.detectors shouldBe None
        v.schema.graph.nodes shouldBe empty
      }
      // default (no param) -> graf: graph kept, no DetectorSchema map
      Get("/schema/0") ~> routes.routes ~> check {
        val v = responseAs[WorkflowSchemaView]
        v.detectors shouldBe None
        v.schema.graph.nodes should have size 3
      }
    }

    "reject paging with only one of from/size" in {
      Get("/schema?from=0") ~> routes.routes ~> check {
        status shouldBe StatusCodes.BadRequest
      }
    }

    "assembly a WorkflowConfig via DSL and read it back with ?entity CSV (graf|detector|schema|all)" in {
      Post("/config/dsl", WorkflowConfigDslReq("Detector.x -> Detector.y", name = Some("WFlow"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfig].graph.isInstance shouldBe true
      }
      Get("/config") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].total shouldBe 1L
      }
      // default (no param) -> graf: graph kept, no detectors/schemas
      Get("/config/0") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val v = responseAs[WorkflowConfigView]
        v.config.graph.nodes should have size 2
        v.detectors shouldBe None
        v.schemas shouldBe None
      }
      // entity=detector -> DetectorConfig by cid; graf stripped
      Get("/config/0?entity=detector") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val v = responseAs[WorkflowConfigView]
        v.detectors.get should have size 2
        v.schemas shouldBe None
        v.config.graph.nodes shouldBe empty
      }
      // entity=schema -> DetectorSchema by node sid; graf stripped
      Get("/config/0?entity=schema") ~> routes.routes ~> check {
        val v = responseAs[WorkflowConfigView]
        v.schemas.get should have size 2
        v.detectors shouldBe None
        v.config.graph.nodes shouldBe empty
      }
      // CSV: detector,schema -> both maps; graf stripped
      Get("/config/0?entity=detector,schema") ~> routes.routes ~> check {
        val v = responseAs[WorkflowConfigView]
        v.detectors.get should have size 2
        v.schemas.get should have size 2
        v.config.graph.nodes shouldBe empty
      }
      // entity=all -> graph + DetectorConfig + DetectorSchema
      Get("/config/0?entity=all") ~> routes.routes ~> check {
        val v = responseAs[WorkflowConfigView]
        v.detectors.get should have size 2
        v.schemas.get should have size 2
        v.config.graph.nodes should have size 2
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

    "resolve WorkflowConfig(s) + all DetectorConfigs by runtimeId or workflowId (multiple ids)" in {
      // assembly two configs, each with its own DetectorConfigs
      val c1 = Post("/config/dsl", WorkflowConfigDslReq("Detector.rx -> Detector.ry", name = Some("R1"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      val c2 = Post("/config/dsl", WorkflowConfigDslReq("Detector.rp -> Detector.rq -> Detector.rr", name = Some("R2"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }

      val RID = "019f51c0-3917-731b-864d-3b9d326db0aa"       // runtimeId (UUID) -> xid
      val WID = "PoR-DefaultProject-1783782976365"           // workflowId       -> meta.wid
      // bind like assembly-track: c1 by runtimeId (xid), c2 by workflowId (meta.wid)
      Await.result(store.addConfig(c1.copy(xid = Some(RID))), 5.seconds)
      Await.result(store.addConfig(c2.copy(meta = Some(Map("wid" -> WID)))), 5.seconds)

      // by runtimeId (UUID, auto-detect) -> c1 + its 2 detectors
      Get(s"/config/resolve/$RID") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        r.configs.head.id shouldBe c1.id
        r.detectors.get should have size 2
      }
      // by workflowId (auto-detect) -> c2
      Get(s"/config/resolve/$WID") ~> routes.routes ~> check {
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 1L
        r.configs.head.id shouldBe c2.id
      }
      // multiple ids in ONE call (comma path) -> both configs + ALL detectors (2+3)
      Get(s"/config/resolve/$RID,$WID") ~> routes.routes ~> check {
        val r = responseAs[WorkflowConfigs]
        r.total shouldBe 2L
        r.configs.map(_.id).toSet shouldBe Set(c1.id, c2.id)
        r.detectors.get should have size 5
      }
      // dedup: RID twice -> once
      Get(s"/config/resolve/$RID,$WID,$RID") ~> routes.routes ~> check {
        responseAs[WorkflowConfigs].total shouldBe 2L
      }
      // type=rid forces runtimeId(xid) matching: WID (a workflowId) must NOT match c2's xid
      Get(s"/config/resolve/$RID,$WID?type=rid") ~> routes.routes ~> check {
        val r = responseAs[WorkflowConfigs]
        r.configs.map(_.id) shouldBe Seq(c1.id) // only c1 (its xid == RID); WID has no xid match
      }
      // type=wid forces workflowId matching: RID (a UUID) must NOT match by meta.wid
      Get(s"/config/resolve/$RID,$WID?type=wid") ~> routes.routes ~> check {
        val r = responseAs[WorkflowConfigs]
        r.configs.map(_.id) shouldBe Seq(c2.id) // only c2 (its meta.wid == WID)
      }
      // unknown id -> empty
      Get("/config/resolve/nope") ~> routes.routes ~> check {
        responseAs[WorkflowConfigs].total shouldBe 0L
      }
    }

    "delete a schema" in {
      Delete("/schema/0") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].status shouldBe WorkflowActionRes.OK
      }
    }

    "POST /config/schema/{sid} creates a WorkflowConfig composed of DetectorConfig (store-assigned ids)" in {
      // a WorkflowSchema whose 2 nodes each reference a DetectorSchema (sid set, no cid)
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.x -> Detector.y", name = Some("WFromSchema"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      sc.graph.nodes.values.foreach { n => n.cid shouldBe None }         // schema nodes have NO DetectorConfig

      val cfg = Post(s"/config/schema/${sc.id}") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      cfg.id should be >= 0                                              // id assigned by the store
      cfg.sid shouldBe sc.id
      val nodes = cfg.graph.nodes.values.toSeq
      nodes should have size 2
      // composed of DetectorConfig: every node now has a cid (and keeps its DetectorSchema sid)
      all (nodes.map(_.cid)) should not be None
      nodes.foreach { n => n.sid should be >= 0 }
      // each cid resolves to a real, persisted DetectorConfig linked to the node's DetectorSchema
      nodes.foreach { n =>
        val cid = n.cid.get
        Get(s"/detector/config/$cid") ~> routes.routes ~> check {
          status shouldBe StatusCodes.OK
          val dc = responseAs[DetectorConfig]
          dc.id shouldBe cid
          dc.schema.map(_.id) shouldBe Some(n.sid)                       // DetectorConfig instantiates the node's DetectorSchema
        }
      }
    }

    "DELETE /config/{id} cascades to its DetectorConfig instances (all deleted)" in {
      val sc = Post("/schema/dsl", WorkflowSchemaDslReq("Detector.p -> Detector.q", name = Some("WCascade"))) ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowSchema]
      }
      val cfg = Post(s"/config/schema/${sc.id}") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK; responseAs[WorkflowConfig]
      }
      val cids = cfg.graph.nodes.values.flatMap(_.cid).toSeq
      cids should have size 2
      cids.foreach { cid => Await.result(store.getDetectorConfig(cid), 5.seconds) should not be None }

      Delete(s"/config/${cfg.id}") ~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].status shouldBe WorkflowActionRes.OK
      }
      Await.result(store.getConfigOpt(cfg.id), 5.seconds) shouldBe None       // config gone
      cids.foreach { cid => Await.result(store.getDetectorConfig(cid), 5.seconds) shouldBe None } // its detectors gone
    }
  }
}
