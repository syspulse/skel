package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.sql.DriverManager

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

import io.syspulse.skel.config.{Configuration, ConfigurationMap}
import io.hacken.ext.wf._
import io.syspulse.skel.wf.ext.store.{WorkflowStoreDB, WorkflowRegistry}
import io.syspulse.skel.wf.ext.server._

/**
 * `setup0` bootstraps the default placement (project id=0 + contract id=0) in the DB so a
 * WorkflowConfig created with contractId=0 (its DetectorConfigs get contract_id=0) satisfies the
 * external `detector.contract_id -> contract(id)` FK. Verified end-to-end: setup0 -> create -> delete.
 */
class Setup0Spec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._

  var embeddedPg: EmbeddedPostgres = _
  var store: WorkflowStoreDB = _
  var routes: WorkflowRoutes = _
  var jdbcUrl: String = _
  val typedSystem = ActorSystem(Behaviors.empty, "Setup0System")

  private def jdbcExec(sql: String): Unit = {
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    try conn.createStatement().execute(sql) finally conn.close()
  }
  private def jdbcCount(sql: String): Long = {
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    try { val rs = conn.createStatement().executeQuery(sql); rs.next(); rs.getLong(1) } finally conn.close()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    embeddedPg = EmbeddedPostgres.builder().start()
    jdbcUrl = s"jdbc:postgresql://localhost:${embeddedPg.getPort()}/postgres"
    // external tables with REAL FKs + NOT-NULL tenant_id (like the upstream schema):
    //   detector.contract_id -> contract(id); contract.project_id -> project(id); *.tenant_id -> tenant(id)
    jdbcExec("CREATE TABLE tenant (id int4 PRIMARY KEY, name text NOT NULL, status text DEFAULT 'ACTIVE' NOT NULL, created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL)")
    jdbcExec("""CREATE TABLE project (
      | id int4 PRIMARY KEY, tenant_id int4 NOT NULL REFERENCES tenant(id),
      | name text NOT NULL, created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL)""".stripMargin)
    jdbcExec("""CREATE TABLE contract (
      | id int4 PRIMARY KEY, project_id int4 NOT NULL REFERENCES project(id),
      | name text DEFAULT '' NOT NULL, created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
      | updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
      | chain_uid text, implementation text, address text)""".stripMargin)
    jdbcExec("""CREATE TABLE detector_schema (
      | id serial4 PRIMARY KEY, created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
      | status text DEFAULT 'ACTIVE' NOT NULL, name text NOT NULL, version text NOT NULL, schema jsonb NOT NULL,
      | tags _text DEFAULT '{}' NOT NULL, description text DEFAULT '' NOT NULL, faq jsonb DEFAULT '"[]"'::jsonb NOT NULL,
      | ui_schema jsonb DEFAULT '{}'::jsonb NOT NULL, author text NULL, icon text NULL, network_tags _text DEFAULT '{}' NOT NULL, title text NULL)""".stripMargin)
    jdbcExec("""CREATE TABLE detector (
      | id serial4 PRIMARY KEY, created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
      | status text DEFAULT 'ACTIVE' NOT NULL, contract_id int4 NOT NULL REFERENCES contract(id), name text NOT NULL, source text NOT NULL,
      | schema_id int4 DEFAULT 1 NOT NULL, tags _text DEFAULT '{}' NOT NULL, config jsonb DEFAULT '{}'::jsonb NOT NULL)""".stripMargin)

    val cfgMap = new ConfigurationMap()
    cfgMap + ("postgres.url", jdbcUrl); cfgMap + ("postgres.database", "postgres")
    cfgMap + ("postgres.username", "postgres"); cfgMap + ("postgres.password", "postgres"); cfgMap + ("postgres.numThreads", "2")
    store = new WorkflowStoreDB(new Configuration(Seq(cfgMap)), "postgres://postgres")

    val engine = new StubEngine
    val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine), "WorkflowRegistry")
    val p = Promise[WorkflowRoutes]()
    typedSystem.systemActorOf(Behaviors.setup[Any] { context => p.success(new WorkflowRoutes(registry, engine)(context, config)); Behaviors.empty }, "test-actor")
    routes = Await.result(p.future, 5.seconds)
  }

  override def afterAll(): Unit = {
    if (store != null) store.ctx.close()
    if (embeddedPg != null) embeddedPg.close()
    typedSystem.terminate(); super.afterAll()
  }

  "POST /setup0" should {
    "create tenant id=0, project id=0 and contract id=0 in the DB (idempotent)" in {
      jdbcCount("SELECT count(*) FROM contract WHERE id=0") shouldBe 0L
      Post("/setup0") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].status shouldBe WorkflowActionRes.OK
      }
      jdbcCount("SELECT count(*) FROM tenant   WHERE id=0 AND name='setup0' AND status='DISABLED'") shouldBe 1L
      jdbcCount("SELECT count(*) FROM project  WHERE id=0 AND tenant_id=0 AND name='setup0'") shouldBe 1L
      jdbcCount("SELECT count(*) FROM contract WHERE id=0 AND project_id=0 AND name='setup0'") shouldBe 1L
      Post("/setup0") ~~> routes.routes ~> check { status shouldBe StatusCodes.OK }   // idempotent
      jdbcCount("SELECT count(*) FROM contract WHERE id=0") shouldBe 1L
    }

    "accept custom tenantId/projectId/contractId/name/status params" in {
      Post("/setup0?tenantId=1&projectId=1&contractId=1&name=custom&status=ACTIVE") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].id shouldBe Some(1)  // contractId echoed back
      }
      jdbcCount("SELECT count(*) FROM tenant   WHERE id=1 AND name='custom' AND status='ACTIVE'") shouldBe 1L
      jdbcCount("SELECT count(*) FROM project  WHERE id=1 AND tenant_id=1 AND name='custom'") shouldBe 1L
      jdbcCount("SELECT count(*) FROM contract WHERE id=1 AND project_id=1 AND name='custom'") shouldBe 1L
    }

    "let a WorkflowConfig be created (DetectorConfig contract_id=0, FK ok) and deleted" in {
      val ds = Await.result(store.addDSchema(
        io.hacken.ext.detector.DetectorSchema(0, 1000L, 1000L, "ACTIVE", "Schema_70", "1.0.0", "t", "", "", None, None, Seq(), Seq(), None, None)), 10.seconds)
      val g = WorkflowGraf(id = 0, sid = None).withNode(WorkflowNode(id = 0, title = "n0", sid = ds.id))
      val ws = Await.result(store.addWSchema(WorkflowSchema.of(0, "WFromSchema", g)), 10.seconds)

      // create the WorkflowConfig from the schema -> DetectorConfig with contract_id=0 (FK satisfied by setup0)
      val cfg = Post(s"/schema/${ws.id}/spawn") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowConfigs].configs.head
      }
      val cid = cfg.graph.nodes.values.head.cid.get
      jdbcCount(s"SELECT count(*) FROM detector WHERE id=$cid AND contract_id=0") shouldBe 1L

      // delete the WorkflowConfig -> cascades to its DetectorConfig(s)
      Delete(s"/config/${cfg.id}") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        responseAs[WorkflowActionRes].status shouldBe WorkflowActionRes.OK
      }
      Await.result(store.getWConfOpt(cfg.id), 10.seconds) shouldBe None  // config gone
      jdbcCount(s"SELECT count(*) FROM detector WHERE id=$cid") shouldBe 0L // its DetectorConfig gone too
    }
  }
}
