package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger

import spray.json._
import DefaultJsonProtocol._

import io.getquill._
import io.getquill.context._

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.store.{Store, StoreDB}

import io.syspulse.skel.explain.{Explain, ExplainScript}
import io.syspulse.skel.explain.server.ExplainScriptJson

// DB-friendly representation: scripts stored as JSON array string
case class Explanation(
  oid: String,
  rid: String,
  scripts: String,   // JSON array string: [{"typ":"js","src":"..."},...]
  name: Option[String],
  describe: Option[String],
  sid: Option[String],
  ts0: Long,
  ts: Long
)

class ExplainStoreDB(configuration: Configuration, dbConfigRef: String)
    extends StoreDB[Explain, String](dbConfigRef, "explanation", Some(configuration))
    with ExplainStore {

  lazy private val log = Logger(getClass)

  def id: String = "db"

  import ctx._
  lazy protected val table = dynamicQuerySchema[Explanation](tableName)

  private def toDb(r: Explain): Explanation = {
    implicit val fmt = ExplainScriptJson.jsonFormat
    Explanation(oidKey(r.oid), r.rid, r.scripts.toJson.compactPrint, r.name, r.desc, r.sid, r.ts0, r.ts)
  }

  private def fromDb(r: Explanation): Explain = {
    implicit val fmt = ExplainScriptJson.jsonFormat
    Explain(
      oid = if(r.oid.isEmpty) None else Some(r.oid),
      rid = r.rid,
      scripts = r.scripts.parseJson.convertTo[Seq[ExplainScript]],
      name = r.name,
      desc = r.describe,
      sid = r.sid,
      ts0 = r.ts0,
      ts = r.ts
    )
  }

  def indexOidRid = "explain_oid_rid"

  def create: Try[Long] = {
    val CREATE_INDEX_OID_RID_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexOidRid} ON ${tableName} (oid, rid);"
    
    val CREATE_INDEX_OID_RID_SQL = getDbType match {
      case "postgres" => CREATE_INDEX_OID_RID_POSTGRES_SQL
    }
    

    val CREATE_TABLE_MYSQL_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        oid VARCHAR(128) NOT NULL,
        rid VARCHAR(128) NOT NULL,
        scripts TEXT,
        name VARCHAR(255),
        describe TEXT,
        sid VARCHAR(128),
        ts0 BIGINT,
        ts BIGINT,
        PRIMARY KEY (oid, rid)
      );"""

    val CREATE_TABLE_POSTGRES_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        oid VARCHAR(128) NOT NULL,
        rid VARCHAR(128) NOT NULL,
        scripts TEXT,
        name VARCHAR(255),
        describe TEXT,
        sid VARCHAR(128),
        ts0 BIGINT,
        ts BIGINT,
        PRIMARY KEY (oid, rid)
      );"""

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql"    => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }

    val r1 = try {
      log.info(s"Table: '${tableName}': ${getDbType}: '${CREATE_TABLE_SQL.replaceAll("\\s+", " ")}'")
      val r = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"Table: '${tableName}': created: ${r}")
      Success(r)
    } catch {
      case e: Exception =>
        log.error(s"failed to create table: '${tableName}': ${e.getMessage()}")
        Failure(e)
    }

    createIndex(indexOidRid,CREATE_INDEX_OID_RID_SQL)

    r1
  }

  def createIndex(indexName:String,indexSQL:String) = {
    try {
      log.info(s"index: '${indexName}': ${getDbType}: '${indexSQL.replaceAll("\\s+"," ")}'")
      val r = ctx.executeAction(indexSQL)(ExecutionInfo.unknown, ())
      log.info(s"index: '${indexName}': created: ${r}")

      Success(r)
    } catch {
      case e1:org.postgresql.util.PSQLException if(e1.getMessage.contains("already exists")) => {
        log.info(s"index: '${indexName}': ${e1.getMessage().replaceFirst("ERROR: ","")}")
        Success(0)
      }
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        // remove ERROR to avpid kubernetes treating it as ERROR
        log.warn(s"failed to create index: '${indexName}': ${e.getMessage().replaceFirst("ERROR: ","")}")
        Failure(e) 
      }
    }
  }

  def all: Seq[Explain] =
    ctx.run(query[Explanation]).map(fromDb)

  def findByOid(oid: Option[String]): Seq[Explain] =
    ctx.run(query[Explanation].filter(r => r.oid == lift(oidKey(oid)))).map(fromDb)

  def get(oid: Option[String], rid: String): Try[Explain] =
    ctx.run(query[Explanation].filter(r => r.oid == lift(oidKey(oid)) && r.rid == lift(rid))) match {
      case h :: _ => Success(fromDb(h))
      case Nil    => Failure(new Exception(s"not found: '$oid/$rid'"))
    }

  def +(r: Explain): Try[Explain] = {
    val dbRule = toDb(r)
    try {
      val q = quote {
        query[Explanation].insertValue(lift(dbRule)).onConflictUpdate(_.oid, _.rid)(
          (t, e) => t.scripts -> e.scripts,
          (t, e) => t.name -> e.name,
          (t, e) => t.describe -> e.describe,
          (t, e) => t.sid -> e.sid,
          (t, e) => t.ts -> e.ts
        )
      }
      ctx.run(q)
      Success(r)
    } catch {
      case e: Exception => Failure(new Exception(s"could not upsert: $r.oid/${r.rid}", e))
    }
  }

  def del(oid: Option[String], rid: String): Try[Explain] =
    get(oid, rid).flatMap { r =>
      try {
        ctx.run(query[Explanation].filter(e => e.oid == lift(oidKey(oid)) && e.rid == lift(rid)).delete)
        Success(r)
      } catch {
        case e: Exception => Failure(e)
      }
    }

  def delByOid(oid: Option[String]): Try[Seq[Explain]] = {
    val existing = findByOid(oid)
    try {
      ctx.run(query[Explanation].filter(r => r.oid == lift(oidKey(oid))).delete)
      Success(existing)
    } catch {
      case e: Exception => Failure(new Exception(s"could not delete rules for oid: '$oid'", e))
    }
  }

  override def size: Long = super.size
}
