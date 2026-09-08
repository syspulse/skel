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
import io.syspulse.skel.wf.ext.store.{WorkflowStore, WorkflowStoreDB}

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
    // LEFT JOIN target of getDConf (read-only; not created by this store)
    st.execute("CREATE TABLE IF NOT EXISTS tenant (id int4 PRIMARY KEY, name text, status text)")
    st.execute("CREATE TABLE IF NOT EXISTS project (id int4 PRIMARY KEY, tenant_id int4, name text)")
    st.execute("""CREATE TABLE IF NOT EXISTS contract (
      | id int4 PRIMARY KEY, project_id int4, name text DEFAULT '',
      | chain_uid text, implementation text, address text,
      | created_at timestamp DEFAULT CURRENT_TIMESTAMP, updated_at timestamp DEFAULT CURRENT_TIMESTAMP)""".stripMargin)
    st.execute("INSERT INTO tenant (id, name, status) VALUES (0, 't', 'ACTIVE') ON CONFLICT DO NOTHING")
    st.execute("INSERT INTO project (id, tenant_id, name) VALUES (0, 0, 'p') ON CONFLICT DO NOTHING")
    st.execute("INSERT INTO contract (id, project_id, name) VALUES (0, 0, 'c') ON CONFLICT DO NOTHING")
    st.close(); conn.close()
    store = newStore() // creates workflow_schema / workflow_config / workflow_graf
  }

  override def afterAll(): Unit = {
    if (store != null) store.ctx.close()
    if (embeddedPg != null) embeddedPg.close()
    super.afterAll()
  }

  private def addSchema(name: String, title: String, description: String = "", tags: Seq[String] = Seq()): WorkflowSchema =
    Await.result(store.addWSchema(schema(0).copy(name = name, title = title, description = description, tags = tags)), timeout)

  private def addConf(name: String, title: String, oid: Option[String] = None, xid: Option[String] = None,
                      description: String = "", updatedAt: Long = System.currentTimeMillis(),
                      status: String = "ACTIVE", tags: Seq[String] = Seq()): WorkflowConfig =
    Await.result(store.addWConf(WorkflowConfig.from(0, schema(0)).copy(
      name = name, title = title, oid = oid, xid = xid, description = description,
      updatedAt = updatedAt, status = status, tags = tags)), timeout)

  private def indexExists(table: String, index: String): Long =
    jdbcCount(s"SELECT count(*) FROM pg_indexes WHERE tablename='$table' AND indexname='$index'")
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

  private def jdbcExec(sql: String): Unit = {
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    try conn.createStatement().execute(sql) finally conn.close()
  }

  private def jdbcLong(sql: String): Long = {
    val conn = DriverManager.getConnection(jdbcUrl, "postgres", "postgres")
    try {
      val rs = conn.createStatement().executeQuery(sql)
      rs.next(); rs.getLong(1)
    } finally conn.close()
  }

  private def seqLast(tbl: String): Long =
    jdbcLong(s"SELECT last_value FROM ${tbl}_id_seq")

  "WorkflowStoreDB (workflow_schema)" should {
    "CRUD + size (id from id_seq, UPDATE keeps id)" in {
      val s = Await.result(store.addWSchema(schema(0)), timeout)
      s.id should be > 0
      Await.result(store.getWSchema(s.id), timeout).name shouldBe "W0"
      Await.result(store.getWSchemaOpt(999), timeout) shouldBe None
      Await.result(store.sizeWSchemas, timeout) shouldBe 1L
      val u = Await.result(store.addWSchema(s.copy(name = "W0b")), timeout) // update by generated id
      u.id shouldBe s.id
      Await.result(store.getWSchema(s.id), timeout).name shouldBe "W0b"
      Await.result(store.sizeWSchemas, timeout) shouldBe 1L
      Await.result(store.delWSchema(s.id), timeout) shouldBe s.id
      Try(Await.result(store.delWSchema(s.id), timeout)).isFailure shouldBe true // not found
    }
    "assign id from id_seq on INSERT (does not send id) and keep the sequence in sync" in {
      val a = Await.result(store.addWSchema(schema(0)), timeout)
      val b = Await.result(store.addWSchema(schema(0)), timeout)
      a.id should be > 0
      b.id should be > a.id
      seqLast("workflow_schema") should be >= b.id.toLong
      // a raw INSERT with an explicit high id leaves id_seq behind; add* still uses nextval
      jdbcExec("INSERT INTO workflow_schema (id, created_at, updated_at, status, name, version, title, description, author, tags, graph) VALUES (9000, 0, 0, 'ACTIVE', 'legacy', '1', 'legacy', '', '', '', '{}')")
      val c = Await.result(store.addWSchema(schema(0)), timeout)
      c.id should not be 9000
      c.id should be > b.id
      Await.result(store.delWSchema(a.id), timeout)
      Await.result(store.delWSchema(b.id), timeout)
      Await.result(store.delWSchema(c.id), timeout)
      jdbcExec("DELETE FROM workflow_schema WHERE id=9000")
    }
    "store faq as string-wrapped array JSON (compatible with detector_schema.faq)" in {
      val faq = Seq(WorkflowSchemaFaq("What is Native Balance Monitor", "Monitors Account/Contract balance (native token)"))
      val s = Await.result(store.addWSchema(schema(0).copy(faq = Some(faq))), timeout)
      val got = Await.result(store.getWSchema(s.id), timeout)
      got.faq shouldBe Some(faq)
      jdbcString(s"SELECT faq FROM workflow_schema WHERE id=${s.id}") shouldBe
        """"[{\"name\":\"What is Native Balance Monitor\",\"value\":\"Monitors Account/Contract balance (native token)\"}]""""
      Await.result(store.delWSchema(s.id), timeout)
    }
    "paginate + nextWSchemaId" in {
      jdbcExec("DELETE FROM workflow_schema")
      val ids = (0 until 5).map(i => Await.result(store.addWSchema(schema(i)), timeout).id).sorted
      Await.result(store.nextWSchemaId, timeout) shouldBe ids.max + 1
      val p = Await.result(store.listWSchemas(Some(1), Some(2)), timeout)
      p.total shouldBe 5L
      p.wschemas.map(_.id) shouldBe ids.slice(1, 3)
    }
  }

  "WorkflowStoreDB (workflow_config)" should {
    "CRUD + findByXid / findByOid + paginate" in {
      val c0 = Await.result(store.addWConf(WorkflowConfig.from(0, schema(0)).copy(xid = Some("Run-XYZ"), oid = Some("owner-1"))), timeout)
      val c1 = Await.result(store.addWConf(WorkflowConfig.from(0, schema(1)).copy(oid = Some("owner-1"))), timeout)
      c0.id should be > 0
      c1.id should be > c0.id

      Await.result(store.getWConf(c0.id), timeout).xid shouldBe Some("Run-XYZ")
      Await.result(store.findWConfByXid("run-xyz"), timeout).map(_.id) shouldBe Some(c0.id)
      Await.result(store.findWConfByOid("owner-1"), timeout).map(_.id).toSet shouldBe Set(c0.id, c1.id)

      val p = Await.result(store.listWConfs(Some(0), Some(1)), timeout)
      p.total shouldBe 2L
      p.wconfs.map(_.id) shouldBe Seq(c0.id)
    }
    "updateWConfStatus updates ONLY the status column" in {
      val c0 = Await.result(store.findWConfByXid("run-xyz"), timeout).get
      Await.result(store.updateWConfStatus(c0.id, "DISABLED"), timeout) shouldBe 1
      val got = Await.result(store.getWConf(c0.id), timeout)
      got.status shouldBe "DISABLED"
      got.xid shouldBe Some("Run-XYZ")
      got.oid shouldBe Some("owner-1")
      Await.result(store.updateWConfStatus(999, "DISABLED"), timeout) shouldBe 0
    }
    "filter by updatedAt range (ts0..ts1) in SQL and create the updated_at index" in {
      jdbcExec("DELETE FROM workflow_config")
      Seq(1000L, 2000L, 3000L).zipWithIndex.foreach { case (ts, i) =>
        Await.result(
          store.addWConf(WorkflowConfig.from(0, schema(0)).copy(updatedAt = ts, oid = Some("owner-ts"))),
          timeout)
      }
      // ts0=1500, ts1=2500 -> only the 2000 one (pushed into the SQL WHERE)
      val mid = Await.result(store.listWConfs(None, None, None, None,
        WorkflowStore.WConfFilter(tsStart = Some(1500L), tsEnd = Some(2500L))), timeout)
      mid.total shouldBe 1L
      mid.wconfs.map(_.updatedAt) shouldBe Seq(2000L)
      // ts0=2000 (inclusive) -> 3000 + 2000 (sorted updatedAt desc)
      Await.result(store.listWConfs(None, None, None, None,
        WorkflowStore.WConfFilter(tsStart = Some(2000L))), timeout).wconfs.map(_.updatedAt) shouldBe Seq(3000L, 2000L)
      // range combined with owner scope (both pushed into SQL)
      Await.result(store.listWConfs(None, None, Some("owner-ts"), None,
        WorkflowStore.WConfFilter(tsEnd = Some(2000L))), timeout).wconfs.map(_.updatedAt).toSet shouldBe Set(1000L, 2000L)
      // the updated_at index was created for fast time-range queries
      jdbcCount(
        "SELECT count(*) FROM pg_indexes WHERE tablename='workflow_config' AND indexname='workflow_config_updated'"
      ) shouldBe 1L
    }
  }

  "WorkflowStoreDB (workflow_graf)" should {
    "CRUD (node.links kept in sync)" in {
      val saved = Await.result(store.addGraf(graf(0)), timeout)
      saved.id should be > 0
      saved.nodes(0).links should not be empty // WorkflowGraf.sync
      Await.result(store.getGraf(saved.id), timeout).links should have size 1
      Await.result(store.sizeGrafs, timeout) shouldBe 1L
    }
  }

  "WorkflowStoreDB (external detector tables)" should {
    "read/write DetectorSchema in detector_schema (id from id_seq)" in {
      jdbcExec("DELETE FROM detector_schema")
      val a = Await.result(store.addDSchema(detSchema(0)), timeout)
      val b = Await.result(store.addDSchema(detSchema(0)), timeout)
      a.id should be > 0
      b.id should be > a.id
      Await.result(store.getDSchema(a.id), timeout).map(_.name) shouldBe Some("Schema_0")
      Await.result(store.sizeDSchemas, timeout) shouldBe 2L
      val p = Await.result(store.listDSchemas(Some(0), Some(1)), timeout)
      p.total shouldBe 2L; p.dschemas.map(_.id) shouldBe Seq(a.id)
      Await.result(store.delDSchema(b.id), timeout) shouldBe b.id
    }
    "keep detector_schema_id_seq in sync when INSERT does not send id" in {
      val a = Await.result(store.addDSchema(detSchema(0)), timeout)
      val b = Await.result(store.addDSchema(detSchema(0)), timeout)
      b.id should be > a.id
      seqLast("detector_schema") should be >= b.id.toLong
      // explicit high id via JDBC leaves the sequence behind; add* still uses nextval (no PK clash)
      jdbcExec("INSERT INTO detector_schema (id, name, version, schema) VALUES (9000, 'legacy', '1.0.0', '{}'::jsonb)")
      val c = Await.result(store.addDSchema(detSchema(0)), timeout)
      c.id should not be 9000
      c.id should be > b.id
      val u = Await.result(store.addDSchema(c.copy(name = "renamed")), timeout)
      u.id shouldBe c.id
      Await.result(store.getDSchema(c.id), timeout).map(_.name) shouldBe Some("renamed")
    }
    "roundtrip DetectorSchema.schema and ui_schema jsonb objects" in {
      val sch = JsObject("type" -> JsString("object"), "properties" -> JsObject(
        "severity" -> JsObject("type" -> JsString("number"), "default" -> JsNumber(0.5))))
      val ui = JsObject("ui:order" -> JsArray(JsString("severity")))
      val s = Await.result(store.addDSchema(detSchema(0).copy(schema = Some(sch), uiSchema = Some(ui))), timeout)
      val got = Await.result(store.getDSchema(s.id), timeout).get
      got.schema shouldBe Some(sch)
      got.uiSchema shouldBe Some(ui)
      jdbcString(s"SELECT jsonb_typeof(ui_schema) FROM detector_schema WHERE id=${s.id}") shouldBe "object"
      jdbcString(s"SELECT jsonb_typeof(schema) FROM detector_schema WHERE id=${s.id}") shouldBe "object"
    }
    "store DetectorSchema.faq as jsonb string (not jsonb array)" in {
      val faq = Seq(DetectorSchemaFaq("What is Native Balance Monitor", "Monitors Account/Contract balance (native token)"))
      val s = Await.result(store.addDSchema(detSchema(0).copy(faq = Some(faq))), timeout)
      val got = Await.result(store.getDSchema(s.id), timeout)
      got.flatMap(_.faq) shouldBe Some(faq)
      jdbcString(s"SELECT faq::text FROM detector_schema WHERE id=${s.id}") shouldBe
        """"[{\"name\":\"What is Native Balance Monitor\",\"value\":\"Monitors Account/Contract balance (native token)\"}]""""
      jdbcString(s"SELECT jsonb_typeof(faq) FROM detector_schema WHERE id=${s.id}") shouldBe "string"
      val empty = Await.result(store.addDSchema(detSchema(0).copy(faq = None)), timeout)
      jdbcString(s"SELECT faq::text FROM detector_schema WHERE id=${empty.id}") shouldBe """"[]""""
      jdbcString(s"SELECT jsonb_typeof(faq) FROM detector_schema WHERE id=${empty.id}") shouldBe "string"
    }
    "read/write DetectorConfig in detector (config is real jsonb; id from id_seq)" in {
      jdbcExec("""INSERT INTO detector(id, contract_id, name, source, config) VALUES (201, 0, 'cfg201', 'SRC', '{"k":"v201"}'::jsonb)""")
      val got = Await.result(store.getDConf(201), timeout)
      got.map(_.name) shouldBe Some("cfg201")
      got.flatMap(_.config).map(_.fields("k")) shouldBe Some(JsString("v201"))
      Await.result(store.getDConf(999), timeout) shouldBe None
      val w = Await.result(store.addDConf(detConfig(0)), timeout)
      w.id should not be 201
      w.id should be > 0
      val loaded = Await.result(store.getDConf(w.id), timeout)
      loaded.map(_.name) shouldBe Some("cfg0")
      loaded.flatMap(_.config).map(_.fields("k")) shouldBe Some(JsString("v0"))
      jdbcCount("SELECT count(*) FROM detector WHERE config->>'k' = 'v0'") shouldBe 1L
      seqLast("detector") should be >= w.id.toLong
    }
    "updateDConfStatus updates ONLY the status column (external detector table)" in {
      Await.result(store.updateDConfStatus(201, "DISABLED"), timeout) shouldBe 1
      val got = Await.result(store.getDConf(201), timeout).get
      got.status shouldBe "DISABLED"
      got.name shouldBe "cfg201"
      got.config.map(_.fields("k")) shouldBe Some(JsString("v201"))
      Await.result(store.updateDConfStatus(999, "DISABLED"), timeout) shouldBe 0
    }
    "createWConfFromWSchema composes a WorkflowConfig of PERSISTED DetectorConfigs (DB)" in {
      val ds = Await.result(store.addDSchema(detSchema(0).copy(name = "Schema_70")), timeout)
      val g = WorkflowGraf(id = 0, sid = None).withNode(WorkflowNode(id = 0, title = "n0", sid = ds.id))
      val ws = Await.result(store.addWSchema(WorkflowSchema.of(0, "WFromSchema", g)), timeout)

      val cfg = Await.result(store.createWConfFromWSchema(ws.id), timeout)
      val node = cfg.graph.nodes.values.head
      node.sid shouldBe ds.id
      node.cid should not be None
      val dc = Await.result(store.getDConf(node.cid.get), timeout)
      dc.map(_.name) shouldBe Some("Schema_70")
      dc.flatMap(_.schema).map(_.id) shouldBe Some(ds.id)
      cfg.id should be > 0
    }
  }

  "WorkflowStoreDB (name/title FTS)" should {
    "create generated tsv GIN indexes on workflow_schema and workflow_config" in {
      jdbcCount("SELECT count(*) FROM information_schema.columns WHERE table_name='workflow_schema' AND column_name='tsv'") shouldBe 1L
      jdbcCount("SELECT count(*) FROM information_schema.columns WHERE table_name='workflow_config' AND column_name='tsv'") shouldBe 1L
      indexExists("workflow_schema", "workflow_schema_fts") shouldBe 1L
      indexExists("workflow_config", "workflow_config_fts") shouldBe 1L
      indexExists("workflow_schema", "workflow_schema_name_trgm") shouldBe 1L
      indexExists("workflow_schema", "workflow_schema_title_trgm") shouldBe 1L
      indexExists("workflow_config", "workflow_config_name_trgm") shouldBe 1L
      indexExists("workflow_config", "workflow_config_title_trgm") shouldBe 1L
    }

    "search schemas by name and title (case-insensitive, prefix) without matching description/tags" in {
      jdbcExec("DELETE FROM workflow_schema")
      val por = addSchema("PoR-Flow", "Proof of Reserve")
      val daily = addSchema("PoR-Flow", "PoR Daily")
      val audit = addSchema("Audit", "Workflow Audit")
      addSchema("zzz", "zzz", description = "SecretFlow hidden in description", tags = Seq("por-tag"))

      val byPor = Await.result(store.listWSchemas(None, None, Some("por")), timeout)
      byPor.total shouldBe 2L
      byPor.wschemas.map(_.id).toSet shouldBe Set(por.id, daily.id)

      val byPorFlow = Await.result(store.listWSchemas(None, None, Some("por-flow")), timeout)
      byPorFlow.total shouldBe 2L
      byPorFlow.wschemas.map(_.id).toSet shouldBe Set(por.id, daily.id)

      val byProof = Await.result(store.listWSchemas(None, None, Some("proof")), timeout)
      byProof.total shouldBe 1L
      byProof.wschemas.head.id shouldBe por.id

      val byAudit = Await.result(store.listWSchemas(None, None, Some("AUDIT")), timeout)
      byAudit.total shouldBe 1L
      byAudit.wschemas.head.id shouldBe audit.id

      val byPrefix = Await.result(store.listWSchemas(None, None, Some("aud")), timeout)
      byPrefix.total shouldBe 1L
      byPrefix.wschemas.head.id shouldBe audit.id

      Await.result(store.listWSchemas(None, None, Some("secretflow")), timeout).total shouldBe 0L
      Await.result(store.listWSchemas(None, None, Some("por-tag")), timeout).total shouldBe 0L
      Await.result(store.listWSchemas(None, None, Some("ab")), timeout).total shouldBe 0L
      Await.result(store.listWSchemas(None, None, Some("")), timeout).total shouldBe 4L
    }

    "page schema search results across page sizes and offsets" in {
      jdbcExec("DELETE FROM workflow_schema")
      val ids = (0 until 10).map(i => addSchema(s"PagerFlow-$i", s"Pager title $i").id).sorted
      addSchema("UnrelatedAudit", "Other")
      val q = Some("pager")
      Seq(1, 2, 3, 4, 5, 7, 10, 11, 100).foreach { size =>
        val p = Await.result(store.listWSchemas(Some(0), Some(size.toLong), q), timeout)
        p.total shouldBe 10L
        p.wschemas.map(_.id) shouldBe ids.take(size)
      }
      val mid = Await.result(store.listWSchemas(Some(3), Some(4), q), timeout)
      mid.total shouldBe 10L
      mid.wschemas.map(_.id) shouldBe ids.slice(3, 7)
      val tail = Await.result(store.listWSchemas(Some(8), Some(5), q), timeout)
      tail.total shouldBe 10L
      tail.wschemas.map(_.id) shouldBe ids.slice(8, 10)
      Await.result(store.listWSchemas(Some(10), Some(3), q), timeout).wschemas shouldBe empty
      val unpaged = Await.result(store.listWSchemas(None, None, q), timeout)
      unpaged.total shouldBe 10L
      unpaged.wschemas.map(_.id) shouldBe ids
    }

    "search configs by name and title (not xid/description), with oid/time filters" in {
      jdbcExec("DELETE FROM workflow_config")
      val a = addConf("PoR-Flow", "Proof of Reserve", oid = Some("owner-a"), xid = Some("flow-runtime"), updatedAt = 1000L)
      val b = addConf("Audit", "Workflow Audit", oid = Some("owner-a"), updatedAt = 2000L)
      addConf("zzz", "zzz", oid = Some("owner-b"), xid = Some("por-xid"), description = "PoR hidden", updatedAt = 3000L)

      val por = Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("por"))), timeout)
      por.total shouldBe 1L
      por.wconfs.map(_.id) shouldBe Seq(a.id)

      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("por-flow"))), timeout)
        .wconfs.map(_.id) shouldBe Seq(a.id)

      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("proof"))), timeout)
        .wconfs.map(_.id) shouldBe Seq(a.id)
      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("aud"))), timeout)
        .wconfs.map(_.id) shouldBe Seq(b.id)

      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("por-xid"))), timeout).total shouldBe 0L
      Await.result(store.listWConfs(None, None, None, None, WorkflowStore.WConfFilter(search = Some("ab"))), timeout).total shouldBe 0L

      val owned = Await.result(store.listWConfs(None, None, Some("owner-a"), None, WorkflowStore.WConfFilter(search = Some("reserve"))), timeout)
      owned.total shouldBe 1L
      owned.wconfs.map(_.id) shouldBe Seq(a.id)

      val ranged = Await.result(store.listWConfs(None, None, None, None,
        WorkflowStore.WConfFilter(search = Some("audit"), tsStart = Some(1500L), tsEnd = Some(2500L))), timeout)
      ranged.total shouldBe 1L
      ranged.wconfs.map(_.id) shouldBe Seq(b.id)

      Await.result(store.listWConfs(None, None, None, None,
        WorkflowStore.WConfFilter(search = Some("audit"), tsEnd = Some(1500L))), timeout).total shouldBe 0L
    }

    "page config search results across page sizes and offsets" in {
      jdbcExec("DELETE FROM workflow_config")
      val ids = (0 until 10).map { i =>
        addConf(s"PagerFlow-$i", s"Pager title $i", updatedAt = 10000L + i).id
      }
      addConf("UnrelatedAudit", "Other", updatedAt = 1L)
      val q = WorkflowStore.WConfFilter(search = Some("pager"), sort = Some("updatedAt:asc"))
      Seq(1, 2, 3, 4, 5, 7, 10, 11, 100).foreach { size =>
        val p = Await.result(store.listWConfs(Some(0), Some(size.toLong), None, None, q), timeout)
        p.total shouldBe 10L
        p.wconfs.map(_.id) shouldBe ids.take(size)
      }
      val mid = Await.result(store.listWConfs(Some(3), Some(4), None, None, q), timeout)
      mid.total shouldBe 10L
      mid.wconfs.map(_.id) shouldBe ids.slice(3, 7)
      val tail = Await.result(store.listWConfs(Some(8), Some(5), None, None, q), timeout)
      tail.total shouldBe 10L
      tail.wconfs.map(_.id) shouldBe ids.slice(8, 10)
      Await.result(store.listWConfs(Some(10), Some(3), None, None, q), timeout).wconfs shouldBe empty
    }
  }
}
