package io.syspulse.skel.wf.ext.store

import scala.util.{Failure, Success, Try}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import com.typesafe.scalalogging.Logger
import com.github.jasync.sql.db.RowData

import spray.json._

import io.getquill._
import io.getquill.context._

import io.syspulse.skel.ErrNotFound
import io.syspulse.skel.config.Configuration
import io.syspulse.skel.store.StoreDBAsync

import io.hacken.ext.wf._
import io.hacken.ext.detector._

// ============================================================================
// WorkflowStoreDB
//
// Postgres-backed WorkflowStore (mirrors ExplainStoreDB). Each entity FIELD is mapped to its own
// database column. Only `JsObject` fields are stored as `jsonb`; scalars are normal columns and
// nested/collection fields (graph, nodes/links, faq, contract, destinations, meta, tags) are held
// as TEXT (JSON / csv).
//
//   workflow_schema / workflow_config / workflow_graf   -> CREATED by this store
//   detector (DetectorConfig) / detector_schema (DetectorSchema)
//                                                       -> OWNED by another product: NEVER created.
//     jsonb columns: workflow_graf.data ; detector_schema.schema, ui_schema ; detector.config
//
// NOTE: Postgres-only (jsonb, ON CONFLICT, `col::text`).
// ============================================================================
class WorkflowStoreDB(configuration: Configuration, dbConfigRef: String)
    extends StoreDBAsync[WorkflowConfig, Int](dbConfigRef, WorkflowStoreDB.TABLE_WORKFLOW_CONFIG, Some(configuration), None)
    with WorkflowStore {

  lazy private val log = Logger(getClass)
  import WorkflowStoreDB._
  import ctx._

  def id: String = "db"

  // ---- JSON formats (referenced explicitly to avoid implicit clashes) ----
  private val fmtGraf   = WorkflowGrafJson.jf_wf_graf
  private val fmtMeta: JsonFormat[Map[String, Any]] = WorkflowGrafJson.jf_metaMap
  private val fmtWfFaq  = DefaultJsonProtocol.seqFormat(WorkflowSchemaJson.jf_wf_faq)
  private val fmtDetFaq = DefaultJsonProtocol.seqFormat(DetectorSchemaJson.jf_faq_item)
  private val fmtContract = DetectorConfigJson.jf_dc_con
  private val fmtDcSchema = DetectorConfigJson.jf_dc_sch
  private val fmtDests    = DefaultJsonProtocol.seqFormat(DetectorConfigJson.jf_dc_dest)

  // ---- SQL literal helpers ----
  private def sqlLit(s: String): String = s.replace("'", "''")
  private def q(s: String): String = s"'${sqlLit(s)}'"
  private def qOpt(o: Option[String]): String = o.map(q).getOrElse("NULL")
  private def lLit(l: Long): String = l.toString
  private def iOpt(o: Option[Int]): String = o.map(_.toString).getOrElse("NULL")
  private def csv(seq: Seq[String]): String = q(seq.mkString(","))
  private def txtJson[T](v: T, w: JsonWriter[T]): String = q(v.toJson(w).compactPrint)
  private def txtJsonOpt[T](o: Option[T], w: JsonWriter[T]): String = o.map(v => q(v.toJson(w).compactPrint)).getOrElse("NULL")
  private def jsonbOpt(o: Option[JsObject]): String = o.map(j => s"'${sqlLit(j.compactPrint)}'::jsonb").getOrElse("NULL")
  private def pageInt(n: Long): Int = n.max(0L).min(Int.MaxValue.toLong).toInt

  // ---- row read helpers ----
  private def rStr(row: RowData, i: Int): String = row.getString(i)
  private def rStrOpt(row: RowData, i: Int): Option[String] = Option(row.getString(i)).filter(_.nonEmpty)
  private def rLong(row: RowData, i: Int): Long = row.getAs[Long](i)
  private def rInt(row: RowData, i: Int): Int = row.getAs[Long](i).toInt
  private def rIntOpt(row: RowData, i: Int): Option[Int] = { val v = row.get(i); if (v == null) None else Some(row.getAs[Long](i).toInt) }
  private def pCsv(s: String): Seq[String] = Option(s).filter(_.nonEmpty).map(_.split(",").toSeq).getOrElse(Seq())
  private def pJsonbObj(s: String): Option[JsObject] = Option(s).filter(_.nonEmpty).map(_.parseJson.asJsObject)
  private def pTxtJson[T](s: String, r: JsonReader[T]): Option[T] = Option(s).filter(_.nonEmpty).map(_.parseJson.convertTo[T](r))
  private def parseGraf(s: String): WorkflowGraf = s.parseJson.convertTo[WorkflowGraf](fmtGraf)

  // ---- generic exec ----
  private def exec(sql: String): Future[Long] = ctx.executeAction(sql)(ExecutionInfo.unknown, ())
  private def countOf(tbl: String, where: String = ""): Future[Long] =
    ctx.executeQuerySingle(s"SELECT count(*) FROM $tbl $where", extractor = (r: RowData, _: Unit) => r.getAs[Long](0))(ExecutionInfo.unknown, ())
  private def nextIdOf(tbl: String): Future[Int] =
    ctx.executeQuerySingle(s"SELECT COALESCE(MAX(id),-1)+1 FROM $tbl", extractor = (r: RowData, _: Unit) => r.getAs[Long](0))(ExecutionInfo.unknown, ()).map(_.toInt)
  private def upsert(tbl: String, cols: Seq[String], vals: Seq[String]): Future[Long] = {
    val set = cols.tail.map(c => s"$c = EXCLUDED.$c").mkString(", ")
    exec(s"INSERT INTO $tbl (${cols.mkString(",")}) VALUES (${vals.mkString(",")}) ON CONFLICT (id) DO UPDATE SET $set")
  }
  private def delById(tbl: String, id: Int, what: String): Future[Int] =
    exec(s"DELETE FROM $tbl WHERE id = $id").flatMap { n =>
      if (n > 0) Future.successful(id) else Future.failed(new ErrNotFound(s"${what}: ${id}"))
    }
  private def limitClause(from: Option[Long], size: Option[Long]): String = (from, size) match {
    case (Some(f), Some(s)) => s"LIMIT ${pageInt(s)} OFFSET ${pageInt(f)}"
    case _                  => ""
  }
  private def query[E](sql: String, extractor: (RowData, Unit) => E): Future[Seq[E]] =
    ctx.executeQuery(sql, extractor = extractor)(ExecutionInfo.unknown, ())

  // ========================================================= create (workflow_* only)
  def create: Try[Long] = {
    val ddl = scala.collection.immutable.ListMap(
      TABLE_WORKFLOW_SCHEMA ->
        s"""CREATE TABLE IF NOT EXISTS ${TABLE_WORKFLOW_SCHEMA} (
         | id BIGINT PRIMARY KEY, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
         | name VARCHAR(255), version VARCHAR(64), title VARCHAR(255), description TEXT, author VARCHAR(255),
         | icon TEXT, faq TEXT, tags TEXT, meta TEXT, graph TEXT)""".stripMargin,
      TABLE_WORKFLOW_CONFIG ->
        s"""CREATE TABLE IF NOT EXISTS ${TABLE_WORKFLOW_CONFIG} (
         | id BIGINT PRIMARY KEY, sid BIGINT, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
         | name VARCHAR(255), version VARCHAR(64), title VARCHAR(255), description TEXT, author VARCHAR(255),
         | icon TEXT, tags TEXT, graph TEXT, oid VARCHAR(128), pid VARCHAR(128), xid VARCHAR(128), meta TEXT)""".stripMargin,
      TABLE_WORKFLOW_GRAF ->
        s"""CREATE TABLE IF NOT EXISTS ${TABLE_WORKFLOW_GRAF} (
         | id BIGINT PRIMARY KEY, sid BIGINT, cid BIGINT, nodes TEXT, links TEXT, meta TEXT, data JSONB)""".stripMargin,
      s"${TABLE_WORKFLOW_CONFIG}_xid" ->
        s"CREATE INDEX IF NOT EXISTS ${TABLE_WORKFLOW_CONFIG}_xid ON ${TABLE_WORKFLOW_CONFIG} (lower(xid))",
      s"${TABLE_WORKFLOW_CONFIG}_oid" ->
        s"CREATE INDEX IF NOT EXISTS ${TABLE_WORKFLOW_CONFIG}_oid ON ${TABLE_WORKFLOW_CONFIG} (oid)",
    )
    
    if (getDbType != "postgres") {
      Failure(new Exception(s"WorkflowStoreDB targets postgres (jsonb); dbType='${getDbType}' may not support the schema"))
    } else 
    {
      var last = 0L
      ddl.foreach { case (name, sql) =>
        log.debug(s"'${name}': ${sql}")
        try {
           val last = Await.result(exec(sql), FiniteDuration(timeout, TimeUnit.MILLISECONDS))        
           log.info(s"'${name}': ${last}")
           last
        } catch {
          case e: Exception => 
            log.warn(s"failed to create: ${name}: ${e.getMessage}");
            -1L;
        }
        
      }
      
      Success(last)
    }
  }

  // ========================================================= Store[WorkflowConfig,Int]
  def getKey(e: WorkflowConfig): Int = e.id
  def +(e: WorkflowConfig): Future[WorkflowConfig] = addConfig(e)
  def del(id: Int): Future[Int] = delConfig(id)
  def ?(id: Int): Future[WorkflowConfig] = getConfig(id)
  def all: Future[Seq[WorkflowConfig]] = allConfigs

  // ========================================================= WorkflowSchema
  private val SCHEMA_COLS = Seq("id","created_at","updated_at","status","name","version","title","description","author","icon","faq","tags","meta","graph")
  private val SCHEMA_SEL  = SCHEMA_COLS.mkString(",")
  private def rowSchema(row: RowData, u: Unit): WorkflowSchema = WorkflowSchema(
    id = rInt(row,0), createdAt = rLong(row,1), updatedAt = rLong(row,2), status = rStr(row,3),
    name = rStr(row,4), version = rStr(row,5), title = rStr(row,6), description = rStr(row,7), author = rStr(row,8),
    icon = rStrOpt(row,9), faq = pTxtJson(rStr(row,10), fmtWfFaq).map(_.toSeq), tags = pCsv(rStr(row,11)),
    meta = pTxtJson(rStr(row,12), fmtMeta), graph = parseGraf(rStr(row,13)))
  private def valsSchema(s: WorkflowSchema): Seq[String] = Seq(
    lLit(s.id), lLit(s.createdAt), lLit(s.updatedAt), q(s.status), q(s.name), q(s.version), q(s.title), q(s.description), q(s.author),
    qOpt(s.icon), txtJsonOpt(s.faq, fmtWfFaq), csv(s.tags), txtJsonOpt(s.meta, fmtMeta), txtJson(s.graph, fmtGraf))

  def addSchema(s: WorkflowSchema): Future[WorkflowSchema] = upsert(TABLE_WORKFLOW_SCHEMA, SCHEMA_COLS, valsSchema(s)).map(_ => s)
  def getSchemaOpt(id: Int): Future[Option[WorkflowSchema]] = query(s"SELECT $SCHEMA_SEL FROM $TABLE_WORKFLOW_SCHEMA WHERE id=$id", rowSchema).map(_.headOption)
  def getSchema(id: Int): Future[WorkflowSchema] = getSchemaOpt(id).map(_.getOrElse(throw new ErrNotFound(s"WorkflowSchema: ${id}")))
  def delSchema(id: Int): Future[Int] = delById(TABLE_WORKFLOW_SCHEMA, id, "WorkflowSchema")
  def allSchemas: Future[Seq[WorkflowSchema]] = query(s"SELECT $SCHEMA_SEL FROM $TABLE_WORKFLOW_SCHEMA ORDER BY id", rowSchema)
  def sizeSchemas: Future[Long] = countOf(TABLE_WORKFLOW_SCHEMA)
  override def nextSchemaId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_WORKFLOW_SCHEMA)
  override def listSchemas(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[WorkflowStore.PageSchema] =
    for {
      total <- sizeSchemas
      items <- query(s"SELECT $SCHEMA_SEL FROM $TABLE_WORKFLOW_SCHEMA ORDER BY id ${limitClause(from,size)}", rowSchema)
    } yield WorkflowStore.PageSchema(items, total)

  // ========================================================= WorkflowConfig
  private val CONFIG_COLS = Seq("id","sid","created_at","updated_at","status","name","version","title","description","author","icon","tags","graph","oid","pid","xid","meta")
  private val CONFIG_SEL  = CONFIG_COLS.mkString(",")
  private def rowConfig(row: RowData, u: Unit): WorkflowConfig = WorkflowConfig(
    id = rInt(row,0), sid = rInt(row,1), createdAt = rLong(row,2), updatedAt = rLong(row,3), status = rStr(row,4),
    name = rStr(row,5), version = rStr(row,6), title = rStr(row,7), description = rStr(row,8), author = rStr(row,9),
    icon = rStrOpt(row,10), tags = pCsv(rStr(row,11)), graph = parseGraf(rStr(row,12)),
    oid = rStrOpt(row,13), pid = rStrOpt(row,14), xid = rStrOpt(row,15), meta = pTxtJson(rStr(row,16), fmtMeta))
  private def valsConfig(c: WorkflowConfig): Seq[String] = Seq(
    lLit(c.id), lLit(c.sid), lLit(c.createdAt), lLit(c.updatedAt), q(c.status), q(c.name), q(c.version), q(c.title), q(c.description), q(c.author),
    qOpt(c.icon), csv(c.tags), txtJson(c.graph, fmtGraf), qOpt(c.oid), qOpt(c.pid), qOpt(c.xid), txtJsonOpt(c.meta, fmtMeta))

  def addConfig(c: WorkflowConfig): Future[WorkflowConfig] = upsert(TABLE_WORKFLOW_CONFIG, CONFIG_COLS, valsConfig(c)).map(_ => c)
  def getConfigOpt(id: Int): Future[Option[WorkflowConfig]] = query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG WHERE id=$id", rowConfig).map(_.headOption)
  def getConfig(id: Int): Future[WorkflowConfig] = getConfigOpt(id).map(_.getOrElse(throw new ErrNotFound(s"WorkflowConfig: ${id}")))
  def delConfig(id: Int): Future[Int] = delById(TABLE_WORKFLOW_CONFIG, id, "WorkflowConfig")
  def allConfigs: Future[Seq[WorkflowConfig]] = query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG ORDER BY id", rowConfig)
  def sizeConfigs: Future[Long] = countOf(TABLE_WORKFLOW_CONFIG)
  def findConfigByOid(oid: String): Future[Seq[WorkflowConfig]] =
    query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG WHERE oid = ${q(oid)} ORDER BY id", rowConfig)
  def findConfigByXid(xid: String): Future[Option[WorkflowConfig]] =
    query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG WHERE lower(xid) = lower(${q(xid)}) LIMIT 1", rowConfig).map(_.headOption)
  override def nextConfigId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_WORKFLOW_CONFIG)
  override def listConfigs(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[WorkflowStore.PageConfig] =
    for {
      total <- sizeConfigs
      items <- query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG ORDER BY id ${limitClause(from,size)}", rowConfig)
    } yield WorkflowStore.PageConfig(items, total)

  // ========================================================= WorkflowGraf  (data -> jsonb)
  private val GRAF_COLS = Seq("id","sid","cid","nodes","links","meta","data")
  private val GRAF_SEL  = "id,sid,cid,nodes,links,meta,data::text"
  private def rowGraf(row: RowData, u: Unit): WorkflowGraf = WorkflowGraf(
    id = rInt(row,0), sid = rIntOpt(row,1), cid = rIntOpt(row,2),
    nodes = pTxtJson[Map[Int, WorkflowNode]](rStr(row,3), WorkflowGrafJson.jf_nodeMap).getOrElse(Map()),
    links = pTxtJson[Map[Int, WorkflowLink]](rStr(row,4), WorkflowGrafJson.jf_linkMap).getOrElse(Map()),
    meta = pTxtJson(rStr(row,5), fmtMeta), data = pJsonbObj(rStr(row,6)))
  private def valsGraf(g: WorkflowGraf): Seq[String] = Seq(
    lLit(g.id), iOpt(g.sid), iOpt(g.cid),
    txtJson(g.nodes, WorkflowGrafJson.jf_nodeMap), txtJson(g.links, WorkflowGrafJson.jf_linkMap),
    txtJsonOpt(g.meta, fmtMeta), jsonbOpt(g.data))

  def addGraf(g: WorkflowGraf): Future[WorkflowGraf] = {
    val g1 = WorkflowGraf.sync(g)
    upsert(TABLE_WORKFLOW_GRAF, GRAF_COLS, valsGraf(g1)).map(_ => g1)
  }
  def getGrafOpt(id: Int): Future[Option[WorkflowGraf]] = query(s"SELECT $GRAF_SEL FROM $TABLE_WORKFLOW_GRAF WHERE id=$id", rowGraf).map(_.headOption)
  def getGraf(id: Int): Future[WorkflowGraf] = getGrafOpt(id).map(_.getOrElse(throw new ErrNotFound(s"WorkflowGraf: ${id}")))
  def delGraf(id: Int): Future[Int] = delById(TABLE_WORKFLOW_GRAF, id, "WorkflowGraf")
  def allGrafs: Future[Seq[WorkflowGraf]] = query(s"SELECT $GRAF_SEL FROM $TABLE_WORKFLOW_GRAF ORDER BY id", rowGraf)
  def sizeGrafs: Future[Long] = countOf(TABLE_WORKFLOW_GRAF)
  override def nextGrafId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_WORKFLOW_GRAF)
  override def listGrafs(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[WorkflowStore.PageGraf] =
    for {
      total <- sizeGrafs
      items <- query(s"SELECT $GRAF_SEL FROM $TABLE_WORKFLOW_GRAF ORDER BY id ${limitClause(from,size)}", rowGraf)
    } yield WorkflowStore.PageGraf(items, total)

  // ========================================================= DetectorSchema (schema, ui_schema -> jsonb) [external]
  private val DSCHEMA_COLS = Seq("id","created_at","updated_at","status","name","version","title","description","author","icon","faq","tags","network_tags","schema","ui_schema")
  private val DSCHEMA_SEL  = "id,created_at,updated_at,status,name,version,title,description,author,icon,faq,tags,network_tags,schema::text,ui_schema::text"
  private def rowDSchema(row: RowData, u: Unit): DetectorSchema = DetectorSchema(
    id = rInt(row,0), createdAt = rLong(row,1), updatedAt = rLong(row,2), status = rStr(row,3),
    name = rStr(row,4), version = rStr(row,5), title = rStr(row,6), description = rStr(row,7), author = rStr(row,8),
    icon = rStrOpt(row,9), faq = pTxtJson(rStr(row,10), fmtDetFaq).map(_.toSeq), tags = pCsv(rStr(row,11)), networkTags = pCsv(rStr(row,12)),
    schema = pJsonbObj(rStr(row,13)), uiSchema = pJsonbObj(rStr(row,14)))
  private def valsDSchema(d: DetectorSchema): Seq[String] = Seq(
    lLit(d.id), lLit(d.createdAt), lLit(d.updatedAt), q(d.status), q(d.name), q(d.version), q(d.title), q(d.description), q(d.author),
    qOpt(d.icon), txtJsonOpt(d.faq, fmtDetFaq), csv(d.tags), csv(d.networkTags), jsonbOpt(d.schema), jsonbOpt(d.uiSchema))

  def addDetectorSchema(d: DetectorSchema): Future[DetectorSchema] = upsert(TABLE_DET_SCHEMA, DSCHEMA_COLS, valsDSchema(d)).map(_ => d)
  def getDetectorSchema(id: Int): Future[Option[DetectorSchema]] = query(s"SELECT $DSCHEMA_SEL FROM $TABLE_DET_SCHEMA WHERE id=$id", rowDSchema).map(_.headOption)
  def delDetectorSchema(id: Int): Future[Int] = delById(TABLE_DET_SCHEMA, id, "DetectorSchema")
  def allDetectorSchemas: Future[Seq[DetectorSchema]] = query(s"SELECT $DSCHEMA_SEL FROM $TABLE_DET_SCHEMA ORDER BY id", rowDSchema)
  def sizeDetectorSchemas: Future[Long] = countOf(TABLE_DET_SCHEMA)
  override def nextDetectorSchemaId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_DET_SCHEMA)
  override def listDetectorSchemas(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[WorkflowStore.PageDetectorSchema] =
    for {
      total <- sizeDetectorSchemas
      items <- query(s"SELECT $DSCHEMA_SEL FROM $TABLE_DET_SCHEMA ORDER BY id ${limitClause(from,size)}", rowDSchema)
    } yield WorkflowStore.PageDetectorSchema(items, total)

  // ========================================================= DetectorConfig (config -> jsonb) [external]
  private val DCONFIG_COLS = Seq("id","created_at","updated_at","status","contract","schema","name","source","tags","config","destinations")
  private val DCONFIG_SEL  = "id,created_at,updated_at,status,contract,schema,name,source,tags,config::text,destinations"
  private def rowDConfig(row: RowData, u: Unit): DetectorConfig = DetectorConfig(
    id = rInt(row,0), createdAt = rLong(row,1), updatedAt = rLong(row,2), status = rStr(row,3),
    contract = rStr(row,4).parseJson.convertTo[DetectorConfigContract](fmtContract),
    schema = pTxtJson(rStr(row,5), fmtDcSchema),
    name = rStr(row,6), source = rStr(row,7), tags = pCsv(rStr(row,8)),
    config = pJsonbObj(rStr(row,9)),
    destinations = pTxtJson(rStr(row,10), fmtDests).map(_.toSeq).getOrElse(Seq()))
  private def valsDConfig(d: DetectorConfig): Seq[String] = Seq(
    lLit(d.id), lLit(d.createdAt), lLit(d.updatedAt), q(d.status),
    txtJson(d.contract, fmtContract), txtJsonOpt(d.schema, fmtDcSchema),
    q(d.name), q(d.source), csv(d.tags), jsonbOpt(d.config), txtJson(d.destinations, fmtDests))

  def addDetectorConfig(d: DetectorConfig): Future[DetectorConfig] = upsert(TABLE_DET_CONFIG, DCONFIG_COLS, valsDConfig(d)).map(_ => d)
  def getDetectorConfig(id: Int): Future[Option[DetectorConfig]] = query(s"SELECT $DCONFIG_SEL FROM $TABLE_DET_CONFIG WHERE id=$id", rowDConfig).map(_.headOption)
  def delDetectorConfig(id: Int): Future[Int] = delById(TABLE_DET_CONFIG, id, "DetectorConfig")
  def allDetectorConfigs: Future[Seq[DetectorConfig]] = query(s"SELECT $DCONFIG_SEL FROM $TABLE_DET_CONFIG ORDER BY id", rowDConfig)
  def sizeDetectorConfigs: Future[Long] = countOf(TABLE_DET_CONFIG)
  override def nextDetectorConfigId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_DET_CONFIG)
  override def listDetectorConfigs(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[WorkflowStore.PageDetectorConfig] =
    for {
      total <- sizeDetectorConfigs
      items <- query(s"SELECT $DCONFIG_SEL FROM $TABLE_DET_CONFIG ORDER BY id ${limitClause(from,size)}", rowDConfig)
    } yield WorkflowStore.PageDetectorConfig(items, total)
}

object WorkflowStoreDB {
  val TABLE_WORKFLOW_SCHEMA     = "workflow_schema"
  val TABLE_WORKFLOW_CONFIG     = "workflow_config"
  val TABLE_WORKFLOW_GRAF       = "workflow_graf"
  val TABLE_DET_CONFIG = "detector"          // external (DetectorConfig)
  val TABLE_DET_SCHEMA = "detector_schema"   // external (DetectorSchema)
}
