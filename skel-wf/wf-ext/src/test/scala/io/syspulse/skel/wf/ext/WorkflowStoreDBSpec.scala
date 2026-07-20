package io.syspulse.skel.wf.ext

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterAll

import scala.util.Try
import spray.json._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import java.sql.DriverManager

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres

import io.syspulse.skel.config.{Configuration, ConfigurationMap}
import io.hacken.ext.wf.{WorkflowSchema, WorkflowConfig, WorkflowGraf, WorkflowNode, WorkflowLink, WorkflowSchemaFaq}
import io.hacken.ext.detector.{DetectorSchema, DetectorConfig, DetectorConfigContract, DetectorSchemaFaq}
import io.syspulse.skel.wf.ext.store.WorkflowStoreDB

class WorkflowStoreDBSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {

  val timeout = Duration(30, "seconds")

  var embeddedPg: EmbeddedPostgres = _
  var store: WorkflowStoreDB = _
  var jdbcUrl: String = _

  private def newStore(): WorkflowStoreDB = {
    val cfgMap = new ConfigurationMap()
    cfgMap + ("postgres.url", jdbcUrl)
    cfgMap + ("postgres.database", "postgres")
    cfgMap + ("postgres.username", "postgres")
    cfgMap + ("postgres.password", "postgres")
    cfgMap + ("postgres.numThreads", "2")
    new WorkflowStoreDB(new Configuration(Seq(cfgMap)), "postgres://postgres")
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    embeddedPg = EmbeddedPostgres.builder().start()
    jdbcUrl = s"jdbc:postgresql://localhost:${embeddedPg.getPort()}/postgres"
    // simulate the external product's tables (the store must NOT create these).
    // Only JsObject fields are jsonb (detector.config ; detector_schema.schema, ui_schema).
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    val st = conn.createStatement()
    // real external schema (timestamps, text[] tags, jsonb NOT NULL). FKs omitted in the fixture
    // so inserts do not require contract / detector_schema referenced rows.
    st.execute(
      """CREATE TABLE IF NOT EXISTS detector_schema (
        | id serial4 PRIMARY KEY,
        | created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
        | updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
        | status text DEFAULT 'ACTIVE' NOT NULL,
        | name text NOT NULL, version text NOT NULL,
        | schema jsonb NOT NULL,
        | tags _text DEFAULT '{}' NOT NULL,
        | description text DEFAULT '' NOT NULL,
        | faq jsonb DEFAULT '"[]"'::jsonb NOT NULL,
        | ui_schema jsonb DEFAULT '{}'::jsonb NOT NULL,
        | author text NULL, icon text NULL,
        | network_tags _text DEFAULT '{}' NOT NULL,
        | title text NULL)""".stripMargin)
    st.execute(
      """CREATE TABLE IF NOT EXISTS detector (
        | id serial4 PRIMARY KEY,
        | created_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
        | updated_at timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL,
        | status text DEFAULT 'ACTIVE' NOT NULL,
        | contract_id int4 NOT NULL,
        | name text NOT NULL, source text NOT NULL,
        | schema_id int4 DEFAULT 1 NOT NULL,
        | tags _text DEFAULT '{}' NOT NULL,
        | config jsonb DEFAULT '{}'::jsonb NOT NULL)""".stripMargin)
    st.close(); conn.close()
    store = newStore() // creates workflow_schema / workflow_config / workflow_graf
  }

  override def afterAll(): Unit = {
    if (store != null) store.ctx.close()
    if (embeddedPg != null) embeddedPg.close()
    super.afterAll()
  }

  // ---- fixtures ----
  private def graf(id: Int): WorkflowGraf =
    WorkflowGraf(id = id, sid = Some(id))
      .withNode(WorkflowNode(id = 0, title = "a", sid = 100))
      .withNode(WorkflowNode(id = 1, title = "b", sid = 101, cid = Some(201)))
      .withLink(WorkflowLink(id = 0, from = 0, to = 1))
  private def schema(id: Int): WorkflowSchema = WorkflowSchema.of(id, s"W${id}", graf(id))

  private def detSchema(id: Int): DetectorSchema = {
    val now = 1000L
    DetectorSchema(id, now, now, "ACTIVE", s"Schema_${id}", "1.0.0", s"Title ${id}", "", "",
      None, None, Seq("t1"), Seq(), None, None)
  }
  private def detConfig(id: Int): DetectorConfig = {
    val now = 1000L
    DetectorConfig(id, now, now, "ACTIVE",
      DetectorConfigContract(0, now, now, 0, 0, None, None, None, None, s"cfg${id}"),
      None, s"cfg${id}", "SRC", Seq("s1"),
      config = Some(JsObject("k" -> JsString(s"v${id}"))), destinations = Seq())
  }

  private def jdbcCount(sql: String): Long = {
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    try {
      val rs = conn.createStatement().executeQuery(sql)
      rs.next(); rs.getLong(1)
    } finally conn.close()
  }

  private def jdbcString(sql: String): String = {
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    try {
      val rs = conn.createStatement().executeQuery(sql)
      rs.next(); rs.getString(1)
    } finally conn.close()
  }

  "WorkflowStoreDB (workflow_schema)" should {
    "CRUD + size" in {
      Await.result(store.addSchema(schema(0)), timeout)
      Await.result(store.getSchema(0), timeout).name shouldBe "W0"
      Await.result(store.getSchemaOpt(999), timeout) shouldBe None
      Await.result(store.sizeSchemas, timeout) shouldBe 1L
      Await.result(store.addSchema(schema(0).copy(name = "W0b")), timeout) // upsert
      Await.result(store.getSchema(0), timeout).name shouldBe "W0b"
      Await.result(store.sizeSchemas, timeout) shouldBe 1L
      Await.result(store.delSchema(0), timeout) shouldBe 0
      Try(Await.result(store.delSchema(0), timeout)).isFailure shouldBe true // not found
    }
    "store faq as string-wrapped array JSON (compatible with detector_schema.faq)" in {
      val faq = Seq(WorkflowSchemaFaq("What is Native Balance Monitor", "Monitors Account/Contract balance (native token)"))
      Await.result(store.addSchema(schema(10).copy(faq = Some(faq))), timeout)
      val got = Await.result(store.getSchema(10), timeout)
      got.faq shouldBe Some(faq)
      // TEXT column holds the same form as detector_schema.faq::text
      jdbcString("SELECT faq FROM workflow_schema WHERE id=10") shouldBe
        """"[{\"name\":\"What is Native Balance Monitor\",\"value\":\"Monitors Account/Contract balance (native token)\"}]""""
      Await.result(store.delSchema(10), timeout) // keep nextSchemaId tests stable
    }
    "paginate + nextSchemaId" in {
      (0 until 5).foreach(i => Await.result(store.addSchema(schema(i)), timeout))
      Await.result(store.nextSchemaId, timeout) shouldBe 5
      val p = Await.result(store.listSchemas(Some(1), Some(2)), timeout)
      p.total shouldBe 5L
      p.schemas.map(_.id) shouldBe Seq(1, 2)
    }
  }

  "WorkflowStoreDB (workflow_config)" should {
    "CRUD + findByXid / findByOid + paginate" in {
      val c0 = WorkflowConfig.from(0, schema(0)).copy(xid = Some("Run-XYZ"), oid = Some("owner-1"))
      val c1 = WorkflowConfig.from(1, schema(1)).copy(oid = Some("owner-1"))
      Await.result(store.addConfig(c0), timeout)
      Await.result(store.addConfig(c1), timeout)

      Await.result(store.getConfig(0), timeout).xid shouldBe Some("Run-XYZ")
      // xid lookup is case-insensitive
      Await.result(store.findConfigByXid("run-xyz"), timeout).map(_.id) shouldBe Some(0)
      Await.result(store.findConfigByOid("owner-1"), timeout).map(_.id).toSet shouldBe Set(0, 1)

      val p = Await.result(store.listConfigs(Some(0), Some(1)), timeout)
      p.total shouldBe 2L
      p.configs.map(_.id) shouldBe Seq(0)
    }
  }

  "WorkflowStoreDB (workflow_graf)" should {
    "CRUD (node.links kept in sync)" in {
      val saved = Await.result(store.addGraf(graf(0)), timeout)
      saved.nodes(0).links should not be empty // WorkflowGraf.sync
      Await.result(store.getGraf(0), timeout).links should have size 1
      Await.result(store.sizeGrafs, timeout) shouldBe 1L
    }
  }

  "WorkflowStoreDB (external detector tables)" should {
    "read/write DetectorSchema in detector_schema" in {
      Await.result(store.addDetectorSchema(detSchema(0)), timeout)
      Await.result(store.addDetectorSchema(detSchema(1)), timeout)
      Await.result(store.getDetectorSchema(0), timeout).map(_.name) shouldBe Some("Schema_0")
      Await.result(store.sizeDetectorSchemas, timeout) shouldBe 2L
      val p = Await.result(store.listDetectorSchemas(Some(0), Some(1)), timeout)
      p.total shouldBe 2L; p.schemas.map(_.id) shouldBe Seq(0)
      Await.result(store.delDetectorSchema(1), timeout) shouldBe 1
    }
    "store DetectorSchema.faq as jsonb string (not jsonb array)" in {
      val faq = Seq(DetectorSchemaFaq("What is Native Balance Monitor", "Monitors Account/Contract balance (native token)"))
      Await.result(store.addDetectorSchema(detSchema(50).copy(faq = Some(faq))), timeout)
      val got = Await.result(store.getDetectorSchema(50), timeout)
      got.flatMap(_.faq) shouldBe Some(faq)
      // jsonb string form: faq::text == "[{\"name\":...}]" ; jsonb_typeof = string
      jdbcString("SELECT faq::text FROM detector_schema WHERE id=50") shouldBe
        """"[{\"name\":\"What is Native Balance Monitor\",\"value\":\"Monitors Account/Contract balance (native token)\"}]""""
      jdbcString("SELECT jsonb_typeof(faq) FROM detector_schema WHERE id=50") shouldBe "string"
      // empty FAQ writes DEFAULT-compatible '"[]"'
      Await.result(store.addDetectorSchema(detSchema(51).copy(faq = None)), timeout)
      jdbcString("SELECT faq::text FROM detector_schema WHERE id=51") shouldBe """"[]""""
      jdbcString("SELECT jsonb_typeof(faq) FROM detector_schema WHERE id=51") shouldBe "string"
    }
    "read/write DetectorConfig in detector (config is real jsonb)" in {
      Await.result(store.addDetectorConfig(detConfig(201)), timeout)
      val got = Await.result(store.getDetectorConfig(201), timeout)
      got.map(_.name) shouldBe Some("cfg201")
      got.flatMap(_.config).map(_.fields("k")) shouldBe Some(JsString("v201")) // JsObject roundtrip
      Await.result(store.getDetectorConfig(999), timeout) shouldBe None
      Await.result(store.sizeDetectorConfigs, timeout) shouldBe 1L
      // the config column is queryable as jsonb (proves it is NOT a text blob)
      jdbcCount("SELECT count(*) FROM detector WHERE config->>'k' = 'v201'") shouldBe 1L
    }
  }
}
