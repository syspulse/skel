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
// nested/collection fields (graph, nodes/links, meta, tags) are held as TEXT (JSON / csv).
// FAQ (`DetectorSchema.faq` / `WorkflowSchema.faq`) uses the upstream string-wrapped array form:
//   "[{\"name\":\"...\",\"value\":\"...\"}]"  (jsonb string on detector_schema; same text on workflow_schema).
//
//   workflow_schema / workflow_config / workflow_graf   -> CREATED by this store (BIGINT ids, TEXT
//                                                          json/csv, `data` is the only jsonb).
//   detector (DetectorConfig) / detector_schema (DetectorSchema)
//                                                       -> OWNED by another product: NEVER created.
//     These use the upstream schema: `timestamp` created_at/updated_at (mapped to/from epoch-ms),
//     `text[]` tags/network_tags, jsonb columns detector_schema.schema/ui_schema/faq + detector.config.
//     Mapped FLAT via DetectorRow / DetectorSchemaRow: schema_id is kept as an id only, and
//     DetectorConfigSchema (details) / destinations are NOT populated. DetectorConfigContract IS
//     populated on READ via a LEFT JOIN to `contract` (+ `project` for tenant_id) - READ-ONLY: the
//     contract/project tables are never written (see toDetectorConfig / DCONFIG_FROM).
//
// NOTE: Postgres-only (jsonb, ON CONFLICT, `col::text`, text[], timestamp arithmetic).
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
  private def optNZ(s: String): Option[String] = Option(s).filter(_.nonEmpty)

  // ---- SQL literal helpers ----
  // NOTE: jasync counts EVERY `?` in the query as a bind placeholder - even inside a quoted string
  // literal - so an inlined value containing `?` (e.g. a workflow result/input JSON) blows up with
  // `InsufficientParametersException: The query contains N parameters but you gave it 0`. We inline all
  // values (no binds), so emit each `?` as chr(63) spliced OUT of the literal (…' || chr(63) || '…) -
  // the final SQL then carries no literal `?`. Callers that wrap the literal in a cast (…::jsonb) must
  // parenthesize it (see jsonb* helpers) because `::` binds tighter than `||`.
  private def sqlLit(s: String): String = s.replace("'", "''").replace("?", "' || chr(63) || '")
  private def q(s: String): String = s"'${sqlLit(s)}'"
  private def qOpt(o: Option[String]): String = o.map(q).getOrElse("NULL")
  private def lLit(l: Long): String = l.toString
  private def iOpt(o: Option[Int]): String = o.map(_.toString).getOrElse("NULL")
  private def csv(seq: Seq[String]): String = q(seq.mkString(","))
  private def txtJson[T](v: T, w: JsonWriter[T]): String = q(v.toJson(w).compactPrint)
  private def txtJsonOpt[T](o: Option[T], w: JsonWriter[T]): String = o.map(v => q(v.toJson(w).compactPrint)).getOrElse("NULL")
  private def jsonbOpt(o: Option[JsObject]): String = o.map(j => s"(${q(j.compactPrint)})::jsonb").getOrElse("NULL")
  private def pageInt(n: Long): Int = n.max(0L).min(Int.MaxValue.toLong).toInt

  // ---- helpers for the EXTERNAL detector tables (real schema: timestamp, text[], jsonb NOT NULL) ----
  private def tsRead(col: String): String = s"(EXTRACT(EPOCH FROM $col)*1000)::bigint" // timestamp -> epoch ms
  private def tsWrite(ms: Long): String = s"(TIMESTAMP 'epoch' + (${ms}/1000.0) * INTERVAL '1 second')" // ms -> timestamp (tz-independent)
  private def pgArr(seq: Seq[String]): String = if (seq.isEmpty) "'{}'::text[]" else s"ARRAY[${seq.map(q).mkString(",")}]::text[]"
  private def pArr(s: String): Seq[String] = pCsv(s) // read via array_to_string(col, ',')
  // jsonb NOT NULL: object defaults to {}, array defaults to []  
  private def jsonbObjReq(o: Option[JsObject]): String = s"(${q(o.map(_.compactPrint).getOrElse("{}"))})::jsonb"
  // FAQ is stored as a jsonb *string* whose content is the array JSON (matches upstream DEFAULT '"[]"'::jsonb).
  // faq::text looks like:
  //   "[{\"name\":\"What is Native Balance Monitor\",\"value\":\"Monitors Account/Contract balance (native token)\"}]"
  // NOT a jsonb array: '[{"name":...}]'::jsonb
  private def faqArrJson[T](o: Option[T], w: JsonWriter[T]): String =
    o.map(_.toJson(w).compactPrint).getOrElse("[]")
  private def jsonbFaqReq[T](o: Option[T], w: JsonWriter[T]): String =
    s"(${q(JsString(faqArrJson(o, w)).compactPrint)})::jsonb"
  // Same encoding for workflow_schema.faq TEXT (column holds the faq::text form above).
  private def txtFaqOpt[T](o: Option[T], w: JsonWriter[T]): String =
    o.map(v => q(JsString(faqArrJson(Some(v), w)).compactPrint)).getOrElse("NULL")

  // faq may be: jsonb-string '"[]"' / '"[{...}]"' (canonical), or legacy jsonb array [{...}]
  private def pFaq[T](s: String, r: JsonReader[scala.collection.Seq[T]]): Option[Seq[T]] = {
    def asSeq(jv: JsValue): Option[Seq[T]] =
      Try(jv.convertTo[scala.collection.Seq[T]](r).toSeq).toOption.filter(_.nonEmpty)
    Option(s).filter(_.nonEmpty).flatMap(t => Try(t.parseJson).toOption).flatMap {
      case a: JsArray   => asSeq(a)
      case JsString(in) => Try(in.parseJson).toOption.flatMap(asSeq)
      case _            => None
    }
  }

  // ---- row read helpers ----
  private def rStr(row: RowData, i: Int): String = row.getString(i)
  private def rStrOpt(row: RowData, i: Int): Option[String] = Option(row.getString(i)).filter(_.nonEmpty)
  // int4/serial4 come back as java Integer, bigint as Long — read via Number to tolerate both.
  private def rNum(row: RowData, i: Int): Number = row.get(i).asInstanceOf[Number]
  private def rLong(row: RowData, i: Int): Long = rNum(row, i).longValue
  private def rInt(row: RowData, i: Int): Int = rNum(row, i).intValue
  private def rIntOpt(row: RowData, i: Int): Option[Int] = { val v = row.get(i); if (v == null) None else Some(v.asInstanceOf[Number].intValue) }
  private def rLongOpt(row: RowData, i: Int): Option[Long] = { val v = row.get(i); if (v == null) None else Some(v.asInstanceOf[Number].longValue) }
  private def pCsv(s: String): Seq[String] = Option(s).filter(_.nonEmpty).map(_.split(",").toSeq).getOrElse(Seq())
  private def pJsonbObj(s: String): Option[JsObject] = Option(s).filter(_.nonEmpty).map(_.parseJson.asJsObject)
  private def pTxtJson[T](s: String, r: JsonReader[T]): Option[T] = Option(s).filter(_.nonEmpty).map(_.parseJson.convertTo[T](r))
  private def parseGraf(s: String): WorkflowGraf = s.parseJson.convertTo[WorkflowGraf](fmtGraf)

  // ---- generic exec ----
  private def exec(sql: String): Future[Long] = ctx.executeAction(sql)(ExecutionInfo.unknown, ())
  // run an UPDATE and return rows affected as Int (uses the context's implicit ec via `import ctx._`)
  private def execUpdateInt(sql: String): Future[Int] = exec(sql).map(_.toInt)
  private def countOf(tbl: String, where: String = ""): Future[Long] =
    ctx.executeQuerySingle(s"SELECT count(*) FROM $tbl $where", extractor = (r: RowData, _: Unit) => r.getAs[Long](0))(ExecutionInfo.unknown, ())
  private def nextIdOf(tbl: String): Future[Int] =
    ctx.executeQuerySingle(s"SELECT COALESCE(MAX(id),-1)+1 FROM $tbl", extractor = (r: RowData, _: Unit) => rInt(r, 0))(ExecutionInfo.unknown, ())
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
         | icon TEXT, faq TEXT, tags TEXT, meta TEXT, graph TEXT, schema JSONB, ui_schema JSONB)""".stripMargin,
      TABLE_WORKFLOW_CONFIG ->
        s"""CREATE TABLE IF NOT EXISTS ${TABLE_WORKFLOW_CONFIG} (
         | id BIGINT PRIMARY KEY, sid BIGINT, created_at BIGINT, updated_at BIGINT, status VARCHAR(64),
         | name VARCHAR(255), version VARCHAR(64), title VARCHAR(255), description TEXT, author VARCHAR(255),
         | icon TEXT, tags TEXT, graph TEXT, oid VARCHAR(128), pid VARCHAR(128), xid VARCHAR(128), meta TEXT, config JSONB)""".stripMargin,
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
  def +(e: WorkflowConfig): Future[WorkflowConfig] = addWConf(e)
  def del(id: Int): Future[Int] = delWConf(id)
  def ?(id: Int): Future[WorkflowConfig] = getWConf(id)
  def all: Future[Seq[WorkflowConfig]] = allWConfs

  // ========================================================= WorkflowSchema
  private val SCHEMA_COLS = Seq("id","created_at","updated_at","status","name","version","title","description","author","icon","faq","tags","meta","graph","schema","ui_schema")
  // schema/ui_schema are jsonb (like detector_schema) -> read them cast to text; the rest are plain columns
  private val SCHEMA_SEL  = Seq("id","created_at","updated_at","status","name","version","title","description","author","icon","faq","tags","meta","graph","schema::text","ui_schema::text").mkString(",")
  private def rowSchema(row: RowData, u: Unit): WorkflowSchema = WorkflowSchema(
    id = rInt(row,0), createdAt = rLong(row,1), updatedAt = rLong(row,2), status = rStr(row,3),
    name = rStr(row,4), version = rStr(row,5), title = rStr(row,6), description = rStr(row,7), author = rStr(row,8),
    icon = rStrOpt(row,9), faq = pFaq(rStr(row,10), fmtWfFaq), tags = pCsv(rStr(row,11)),
    meta = pTxtJson(rStr(row,12), fmtMeta), graph = parseGraf(rStr(row,13)),
    schema = pJsonbObj(rStr(row,14)), uiSchema = pJsonbObj(rStr(row,15)))
  private def valsSchema(wschema: WorkflowSchema): Seq[String] = Seq(
    lLit(wschema.id), lLit(wschema.createdAt), lLit(wschema.updatedAt), q(wschema.status), q(wschema.name), q(wschema.version), q(wschema.title), q(wschema.description), q(wschema.author),
    qOpt(wschema.icon), txtFaqOpt(wschema.faq, fmtWfFaq), csv(wschema.tags), txtJsonOpt(wschema.meta, fmtMeta), txtJson(wschema.graph, fmtGraf),
    jsonbOpt(wschema.schema), jsonbOpt(wschema.uiSchema))

  def addWSchema(wschema: WorkflowSchema): Future[WorkflowSchema] = upsert(TABLE_WORKFLOW_SCHEMA, SCHEMA_COLS, valsSchema(wschema)).map(_ => wschema)
  def getWSchemaOpt(id: Int): Future[Option[WorkflowSchema]] = query(s"SELECT $SCHEMA_SEL FROM $TABLE_WORKFLOW_SCHEMA WHERE id=$id", rowSchema).map(_.headOption)
  def getWSchema(id: Int): Future[WorkflowSchema] = getWSchemaOpt(id).map(_.getOrElse(throw new ErrNotFound(s"WorkflowSchema: ${id}")))
  def delWSchema(id: Int): Future[Int] = delById(TABLE_WORKFLOW_SCHEMA, id, "WorkflowSchema")
  def allWSchemas: Future[Seq[WorkflowSchema]] = query(s"SELECT $SCHEMA_SEL FROM $TABLE_WORKFLOW_SCHEMA ORDER BY id", rowSchema)
  def sizeWSchemas: Future[Long] = countOf(TABLE_WORKFLOW_SCHEMA)
  override def nextWSchemaId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_WORKFLOW_SCHEMA)
  override def listWSchemas(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[WorkflowStore.PageWSchema] =
    for {
      total <- sizeWSchemas
      items <- query(s"SELECT $SCHEMA_SEL FROM $TABLE_WORKFLOW_SCHEMA ORDER BY id ${limitClause(from,size)}", rowSchema)
    } yield WorkflowStore.PageWSchema(items, total)

  // ========================================================= WorkflowConfig
  private val CONFIG_COLS = Seq("id","sid","created_at","updated_at","status","name","version","title","description","author","icon","tags","graph","oid","pid","xid","meta","config")
  // config is jsonb (like detector.config) -> read it cast to text; the rest are plain columns
  private val CONFIG_SEL  = Seq("id","sid","created_at","updated_at","status","name","version","title","description","author","icon","tags","graph","oid","pid","xid","meta","config::text").mkString(",")
  private def rowConfig(row: RowData, u: Unit): WorkflowConfig = WorkflowConfig(
    id = rInt(row,0), sid = rInt(row,1), createdAt = rLong(row,2), updatedAt = rLong(row,3), status = rStr(row,4),
    name = rStr(row,5), version = rStr(row,6), title = rStr(row,7), description = rStr(row,8), author = rStr(row,9),
    icon = rStrOpt(row,10), tags = pCsv(rStr(row,11)), graph = parseGraf(rStr(row,12)),
    oid = rStrOpt(row,13), pid = rStrOpt(row,14), xid = rStrOpt(row,15), meta = pTxtJson(rStr(row,16), fmtMeta),
    config = pJsonbObj(rStr(row,17)))
  private def valsConfig(wconf: WorkflowConfig): Seq[String] = Seq(
    lLit(wconf.id), lLit(wconf.sid), lLit(wconf.createdAt), lLit(wconf.updatedAt), q(wconf.status), q(wconf.name), q(wconf.version), q(wconf.title), q(wconf.description), q(wconf.author),
    qOpt(wconf.icon), csv(wconf.tags), txtJson(wconf.graph, fmtGraf), qOpt(wconf.oid), qOpt(wconf.pid), qOpt(wconf.xid), txtJsonOpt(wconf.meta, fmtMeta),
    jsonbOpt(wconf.config))

  def addWConf(wconf: WorkflowConfig): Future[WorkflowConfig] = upsert(TABLE_WORKFLOW_CONFIG, CONFIG_COLS, valsConfig(wconf)).map(_ => wconf)
  def getWConfOpt(id: Int): Future[Option[WorkflowConfig]] = query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG WHERE id=$id", rowConfig).map(_.headOption)
  def getWConf(id: Int): Future[WorkflowConfig] = getWConfOpt(id).map(_.getOrElse(throw new ErrNotFound(s"WorkflowConfig: ${id}")))
  def delWConf(id: Int): Future[Int] = delById(TABLE_WORKFLOW_CONFIG, id, "WorkflowConfig")
  def allWConfs: Future[Seq[WorkflowConfig]] = query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG ORDER BY id", rowConfig)
  def sizeWConfs: Future[Long] = countOf(TABLE_WORKFLOW_CONFIG)
  def findWConfByOid(oid: String): Future[Seq[WorkflowConfig]] =
    query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG WHERE oid = ${q(oid)} ORDER BY id", rowConfig)
  def findWConfByXid(xid: String): Future[Option[WorkflowConfig]] =
    query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG WHERE lower(xid) = lower(${q(xid)}) LIMIT 1", rowConfig).map(_.headOption)
  // optimized status-only update (single column + updated_at); no read, no full-row rewrite
  override def updateWConfStatus(id: Int, status: String)(implicit ec: ExecutionContext): Future[Int] =
    execUpdateInt(s"UPDATE $TABLE_WORKFLOW_CONFIG SET status=${q(status)}, updated_at=${lLit(System.currentTimeMillis())} WHERE id=$id")
  override def nextWConfId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_WORKFLOW_CONFIG)
  override def listWConfs(from: Option[Long], size: Option[Long],
                          oid: Option[String] = None, pid: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageWConf] = {
    val where = Seq(oid.map(o => s"oid = ${q(o)}"), pid.map(p => s"pid = ${q(p)}")).flatten match {
      case Nil => ""
      case xs  => "WHERE " + xs.mkString(" AND ")
    }
    for {
      total <- countOf(TABLE_WORKFLOW_CONFIG, where)
      items <- query(s"SELECT $CONFIG_SEL FROM $TABLE_WORKFLOW_CONFIG $where ORDER BY id ${limitClause(from,size)}", rowConfig)
    } yield WorkflowStore.PageWConf(items, total)
  }

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

  // ========================================================= DetectorSchema  [EXTERNAL table "detector_schema"]
  // Real columns (timestamp, text[], jsonb NOT NULL). All DetectorSchema fields map directly (no joins).
  private val DSCHEMA_COLS = Seq("id","created_at","updated_at","status","name","version","schema","tags","description","faq","ui_schema","author","icon","network_tags","title")
  private val DSCHEMA_SEL  =
    s"id,${tsRead("created_at")},${tsRead("updated_at")},status,name,version,title,description,author,icon,faq::text,array_to_string(tags,','),array_to_string(network_tags,','),schema::text,ui_schema::text"
  private def rowDSchemaRow(row: RowData, u: Unit): DetectorSchemaRow = DetectorSchemaRow(
    id = rInt(row,0), createdAt = rLong(row,1), updatedAt = rLong(row,2), status = rStr(row,3),
    name = rStr(row,4), version = rStr(row,5), title = rStrOpt(row,6), description = rStr(row,7), author = rStrOpt(row,8),
    icon = rStrOpt(row,9), faq = pFaq(rStr(row,10), fmtDetFaq), tags = pArr(rStr(row,11)), networkTags = pArr(rStr(row,12)),
    schema = pJsonbObj(rStr(row,13)), uiSchema = pJsonbObj(rStr(row,14)))
  private def rowDSchema(row: RowData, u: Unit): DetectorSchema = toDetectorSchema(rowDSchemaRow(row, u))
  private def valsDSchema(dschema: DetectorSchema): Seq[String] = { // column order = DSCHEMA_COLS
    Seq(lLit(dschema.id), tsWrite(dschema.createdAt), tsWrite(dschema.updatedAt), q(dschema.status), q(dschema.name), q(dschema.version),
      jsonbObjReq(dschema.schema), pgArr(dschema.tags), q(dschema.description), jsonbFaqReq(dschema.faq, fmtDetFaq), jsonbObjReq(dschema.uiSchema),
      qOpt(optNZ(dschema.author)), qOpt(dschema.icon), pgArr(dschema.networkTags), qOpt(optNZ(dschema.title)))
  }

  def addDSchema(dschema: DetectorSchema): Future[DetectorSchema] = upsert(TABLE_DET_SCHEMA, DSCHEMA_COLS, valsDSchema(dschema)).map(_ => dschema)
  def getDSchema(id: Int): Future[Option[DetectorSchema]] = query(s"SELECT $DSCHEMA_SEL FROM $TABLE_DET_SCHEMA WHERE id=$id", rowDSchema).map(_.headOption)
  def delDSchema(id: Int): Future[Int] = delById(TABLE_DET_SCHEMA, id, "DetectorSchema")
  def allDSchemas: Future[Seq[DetectorSchema]] = query(s"SELECT $DSCHEMA_SEL FROM $TABLE_DET_SCHEMA ORDER BY id", rowDSchema)
  def sizeDSchemas: Future[Long] = countOf(TABLE_DET_SCHEMA)
  override def nextDSchemaId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_DET_SCHEMA)
  override def listDSchemas(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[WorkflowStore.PageDSchema] =
    for {
      total <- sizeDSchemas
      items <- query(s"SELECT $DSCHEMA_SEL FROM $TABLE_DET_SCHEMA ORDER BY id ${limitClause(from,size)}", rowDSchema)
    } yield WorkflowStore.PageDSchema(items, total)

  // ========================================================= DetectorConfig  [EXTERNAL table "detector"]
  // WRITE columns use contract_id / schema_id FKs only (DCONFIG_COLS). READ additionally LEFT JOINs the
  // upstream `contract` and `project` tables to populate the FULL DetectorConfigContract - READ-ONLY:
  // the contract/project tables are NEVER inserted/updated/deleted here.
  //   contract.*        -> id, created_at, updated_at, project_id, chain_uid, implementation, address, name
  //   project.tenant_id -> tenantId  (tenant_id lives on `project`, not `contract`)
  //   proxyAddress is DEPRECATED and NOT a DB column: derived as `address` when `implementation` is set.
  private val DCONFIG_COLS = Seq("id","created_at","updated_at","status","contract_id","name","source","schema_id","tags","config")
  // read: alias detector d; LEFT JOIN so a detector with an orphan/missing contract is still returned
  private val DCONFIG_FROM =
    s"$TABLE_DET_CONFIG d LEFT JOIN contract c ON d.contract_id = c.id LEFT JOIN project p ON c.project_id = p.id"
  private val DCONFIG_SEL  =
    s"d.id,${tsRead("d.created_at")},${tsRead("d.updated_at")},d.status,d.contract_id,d.name,d.source,d.schema_id," +
    s"array_to_string(d.tags,','),d.config::text," +
    s"c.project_id,p.tenant_id,c.name,${tsRead("c.created_at")},${tsRead("c.updated_at")}," +
    s"c.chain_uid,c.implementation,c.address"
  private def rowDRow(row: RowData, u: Unit): DetectorRow = DetectorRow(
    id = rInt(row,0), createdAt = rLong(row,1), updatedAt = rLong(row,2), status = rStr(row,3),
    contractId = rInt(row,4), name = rStr(row,5), source = rStr(row,6), schemaId = rInt(row,7),
    tags = pArr(row.getString(8)), config = pJsonbObj(rStr(row,9)),
    contractProjectId = rIntOpt(row,10).getOrElse(-1), contractTenantId = rIntOpt(row,11).getOrElse(-1),
    contractName = rStrOpt(row,12).getOrElse(""),
    contractCreatedAt = rLongOpt(row,13).getOrElse(0L), contractUpdatedAt = rLongOpt(row,14).getOrElse(0L),
    contractChainUid = rStrOpt(row,15),
    contractImplementation = rStrOpt(row,16), contractAddress = rStrOpt(row,17))
  private def rowDConfig(row: RowData, u: Unit): DetectorConfig = toDetectorConfig(rowDRow(row, u))
  private def valsDConfig(dconf: DetectorConfig): Seq[String] = { // column order = DCONFIG_COLS (detector table only)
    val r = toDetectorRow(dconf)
    Seq(lLit(r.id), tsWrite(r.createdAt), tsWrite(r.updatedAt), q(r.status), lLit(r.contractId), q(r.name), q(r.source), lLit(r.schemaId), pgArr(r.tags), jsonbObjReq(r.config))
  }

  // Bootstrap the default placement: tenant -> project -> contract (idempotent). This lets a
  // DetectorConfig created with `contractId` satisfy the external `detector.contract_id` -> contract(id)
  // FK. Note: only `project` carries `tenant_id`; `contract` does not.
  override def setup0(tenantId: Int, projectId: Int, contractId: Int, name: String, status: String)(implicit ec: ExecutionContext): Future[Unit] =
    setup0Db(tenantId, projectId, contractId, name, status)
  private def setup0Db(tenantId: Int, projectId: Int, contractId: Int, name: String, status: String): Future[Unit] = // uses the context's implicit ec
    exec(s"INSERT INTO tenant   (id, name, status) VALUES (${lLit(tenantId)}, ${q(name)}, ${q(status)}) ON CONFLICT (id) DO NOTHING")
      .flatMap(_ => exec(s"INSERT INTO project  (id, tenant_id, name) VALUES (${lLit(projectId)}, ${lLit(tenantId)}, ${q(name)}) ON CONFLICT (id) DO NOTHING"))
      .flatMap(_ => exec(s"INSERT INTO contract (id, project_id, name) VALUES (${lLit(contractId)}, ${lLit(projectId)}, ${q(name)}) ON CONFLICT (id) DO NOTHING"))
      .map(_ => ())

  // Write DetectorConfig to the EXTERNAL `detector` table (upsert on id). Only the flat columns are
  // written (contract/schema are FK ids; destinations are not persisted - see DCONFIG_COLS/valsDConfig).
  def addDConf(dconf: DetectorConfig): Future[DetectorConfig] = upsert(TABLE_DET_CONFIG, DCONFIG_COLS, valsDConfig(dconf)).map(_ => dconf)
  // status-only update on the external `detector` table (single column + updated_at timestamp)
  override def updateDConfStatus(id: Int, status: String)(implicit ec: ExecutionContext): Future[Int] =
    execUpdateInt(s"UPDATE $TABLE_DET_CONFIG SET status=${q(status)}, updated_at=${tsWrite(System.currentTimeMillis())} WHERE id=$id")
  def getDConf(id: Int): Future[Option[DetectorConfig]] = query(s"SELECT $DCONFIG_SEL FROM $DCONFIG_FROM WHERE d.id=$id", rowDConfig).map(_.headOption)
  def delDConf(id: Int): Future[Int] = delById(TABLE_DET_CONFIG, id, "DetectorConfig")
  def allDConfs: Future[Seq[DetectorConfig]] = query(s"SELECT $DCONFIG_SEL FROM $DCONFIG_FROM ORDER BY d.id", rowDConfig)
  def sizeDConfs: Future[Long] = countOf(TABLE_DET_CONFIG)
  override def nextDConfId(implicit ec: ExecutionContext): Future[Int] = nextIdOf(TABLE_DET_CONFIG)
  // External `detector` table has no oid/pid columns - filter in memory after load (Mem/Dir persist them).
  override def listDConfs(from: Option[Long], size: Option[Long],
                          oid: Option[String] = None, pid: Option[String] = None)(implicit ec: ExecutionContext): Future[WorkflowStore.PageDConf] =
    super.listDConfs(from, size, oid, pid)
}

object WorkflowStoreDB {
  val TABLE_WORKFLOW_SCHEMA     = "workflow_schema"
  val TABLE_WORKFLOW_CONFIG     = "workflow_config"
  val TABLE_WORKFLOW_GRAF       = "workflow_graf"
  val TABLE_DET_CONFIG = "detector"          // external (DetectorConfig)
  val TABLE_DET_SCHEMA = "detector_schema"   // external (DetectorSchema)

  // ---- flat DB rows matching the EXTERNAL detector tables (no JOINs) ----
  // detector_schema: all DetectorSchema fields map directly.
  case class DetectorSchemaRow(
    id: Int, createdAt: Long, updatedAt: Long, status: String, name: String, version: String,
    title: Option[String], description: String, author: Option[String], icon: Option[String],
    faq: Option[Seq[DetectorSchemaFaq]], tags: Seq[String], networkTags: Seq[String],
    schema: Option[JsObject], uiSchema: Option[JsObject])
  // detector: uses contract_id / schema_id FKs instead of nested objects.
  // contract* are populated READ-ONLY via a LEFT JOIN (contract + project); they are NEVER written back.
  //   contractProjectId <- contract.project_id ;  contractTenantId <- project.tenant_id ;  the rest <- contract.*
  case class DetectorRow(
    id: Int, createdAt: Long, updatedAt: Long, status: String, contractId: Int, name: String,
    source: String, schemaId: Int, tags: Seq[String], config: Option[JsObject],
    contractProjectId: Int = -1, contractTenantId: Int = -1, contractName: String = "",
    contractCreatedAt: Long = 0L, contractUpdatedAt: Long = 0L,
    contractChainUid: Option[String] = None, contractProxyAddress: Option[String] = None,
    contractImplementation: Option[String] = None, contractAddress: Option[String] = None)

  def toDetectorSchema(r: DetectorSchemaRow): DetectorSchema =
    DetectorSchema(r.id, r.createdAt, r.updatedAt, r.status, r.name, r.version,
      r.title.getOrElse(""), r.description, r.author.getOrElse(""), r.icon, r.faq, r.tags, r.networkTags, r.schema, r.uiSchema)

  def toDetectorConfig(r: DetectorRow): DetectorConfig =
    DetectorConfig(r.id, r.createdAt, r.updatedAt, r.status,
      // full contract read (LEFT JOIN contract + project); tenantId comes from project.tenant_id
      contract = DetectorConfigContract(
        id = r.contractId, createdAt = r.contractCreatedAt, updatedAt = r.contractUpdatedAt,
        projectId = r.contractProjectId, tenantId = r.contractTenantId,
        chainUid = r.contractChainUid,
        // proxyAddress is deprecated and not stored: it is `address` when an implementation address exists
        proxyAddress = if (r.contractImplementation.exists(_.trim.nonEmpty)) r.contractAddress else None,
        implementation = r.contractImplementation, address = r.contractAddress,
        name = r.contractName),
      schema = Some(DetectorConfigSchema(r.schemaId, 0L, 0L, "", "", "", None)),
      name = r.name, source = r.source, tags = r.tags, config = r.config, destinations = Seq())

  // WRITE mapping: only the `detector` table columns (contract_id FK). The contract* fields are carried
  // for completeness but are NEVER written (see DCONFIG_COLS / valsDConfig) - the contract is read-only.
  def toDetectorRow(d: DetectorConfig): DetectorRow =
    DetectorRow(d.id, d.createdAt, d.updatedAt, d.status, d.contract.id, d.name, d.source,
      d.schema.map(_.id).getOrElse(1), d.tags, d.config,
      contractProjectId = d.contract.projectId, contractTenantId = d.contract.tenantId, contractName = d.contract.name,
      contractCreatedAt = d.contract.createdAt, contractUpdatedAt = d.contract.updatedAt,
      contractChainUid = d.contract.chainUid, contractProxyAddress = d.contract.proxyAddress,
      contractImplementation = d.contract.implementation, contractAddress = d.contract.address)
}
