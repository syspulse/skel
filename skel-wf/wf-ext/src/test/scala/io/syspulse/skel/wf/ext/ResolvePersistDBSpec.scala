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
import io.syspulse.skel.wf.ext.engine.EngineStatus

/**
 * Resolve must write the Engine truth back into the store, but the DB store (WorkflowStoreDB) owns
 * `detector` EXTERNALLY - so it may update WorkflowConfig.status but must NOT update DetectorConfig
 * for now (`canUpdateDetectorConfig == false`).
 */
class ResolvePersistDBSpec extends AnyWordSpec with Matchers with ScalatestRouteTest with BeforeAndAfterAll with WfRouteTest {

  import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
  import WorkflowJson._
  import io.hacken.ext.wf.WorkflowConfigJson._

  var embeddedPg: EmbeddedPostgres = _
  var store: WorkflowStoreDB = _
  var routes: WorkflowRoutes = _
  var jdbcUrl: String = _
  val typedSystem = ActorSystem(Behaviors.empty, "ResolvePersistDBSystem")

  private def jdbcExec(sql: String): Unit = {
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    try conn.createStatement().execute(sql) finally conn.close()
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    embeddedPg = EmbeddedPostgres.builder().start()
    jdbcUrl = s"jdbc:postgresql://localhost:${embeddedPg.getPort()}/postgres"
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    val st = conn.createStatement()
    st.execute(
      """CREATE TABLE IF NOT EXISTS detector_schema (
        | id serial4 PRIMARY KEY, created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
        | updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, status text DEFAULT 'ACTIVE' NOT NULL,
        | name text NOT NULL, version text NOT NULL, schema jsonb NOT NULL, tags _text DEFAULT '{}' NOT NULL,
        | description text DEFAULT '' NOT NULL, faq jsonb DEFAULT '"[]"'::jsonb NOT NULL, ui_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
        | author text NULL, icon text NULL, network_tags _text DEFAULT '{}' NOT NULL, title text NULL)""".stripMargin)
    st.execute(
      """CREATE TABLE IF NOT EXISTS detector (
        | id serial4 PRIMARY KEY, created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
        | updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, status text DEFAULT 'ACTIVE' NOT NULL,
        | contract_id int4 NOT NULL, name text NOT NULL, source text NOT NULL, schema_id int4 DEFAULT 1 NOT NULL,
        | tags _text DEFAULT '{}' NOT NULL, config jsonb DEFAULT '{}'::jsonb NOT NULL)""".stripMargin)
    st.close(); conn.close()

    val cfgMap = new ConfigurationMap()
    cfgMap + ("postgres.url", jdbcUrl)
    cfgMap + ("postgres.database", "postgres")
    cfgMap + ("postgres.username", "postgres")
    cfgMap + ("postgres.password", "postgres")
    cfgMap + ("postgres.numThreads", "2")
    store = new WorkflowStoreDB(new Configuration(Seq(cfgMap)), "postgres://postgres")

    val engine = Some(new StubEngine)
    val registry = typedSystem.systemActorOf(WorkflowRegistry(store, engine), "WorkflowRegistry")
    val p = Promise[WorkflowRoutes]()
    typedSystem.systemActorOf(Behaviors.setup[Any] { context => p.success(new WorkflowRoutes(registry, engine)(context, config)); Behaviors.empty }, "test-actor")
    routes = Await.result(p.future, 5.seconds)
  }

  override def afterAll(): Unit = {
    if (store != null) store.ctx.close()
    if (embeddedPg != null) embeddedPg.close()
    typedSystem.terminate()
    super.afterAll()
  }

  "Resolve with a DB store" should {
    "persist WorkflowConfig.status AND (status-only) DetectorConfig.status to the external table" in {
      val RID = "684d1e0d-acf2-4103-9318-8b835b8626c2"
      // the external product owns `detector`: seed the row (name matches the Engine activity type)
      jdbcExec("INSERT INTO detector(id, status, contract_id, name, source) VALUES (201, 'ACTIVE', 0, 'ProofOfOwnership', 'SRC')")

      // a WorkflowConfig bound to the runtime (xid == RID), with a node referencing DetectorConfig 201
      val g = WorkflowGraf(id = 1, sid = Some(1)).withNode(WorkflowNode(id = 0, title = "poo", sid = 100, cid = Some(201)))
      val sc = WorkflowSchema.of(1, "W1", g)
      Await.result(store.addWSchema(sc), 10.seconds)
      Await.result(store.addWConf(WorkflowConfig.from(1, sc, xid = Some(RID)).copy(status = "ACTIVE")), 10.seconds)

      Get(s"/config/resolve/$RID?type=rid") ~~> routes.routes ~> check {
        status shouldBe StatusCodes.OK
        val r = responseAs[WorkflowConfigs]
        r.configs.head.status shouldBe EngineStatus.RUNNING              // response reflects the Engine
        r.detectors.get.apply("201").status shouldBe EngineStatus.COMPLETED  // matched activity (live)
      }

      // both statuses are persisted via the optimized status-only UPDATE (DetectorConfig via `detector`)
      Await.result(store.getWConf(1), 10.seconds).status shouldBe EngineStatus.RUNNING
      Await.result(store.getDConf(201), 10.seconds).get.status shouldBe EngineStatus.COMPLETED
      // the status-only UPDATE touched ONLY status (name/source untouched)
      Await.result(store.getDConf(201), 10.seconds).get.name shouldBe "ProofOfOwnership"
    }
  }
}
