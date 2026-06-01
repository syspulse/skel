package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import spray.json._
import DefaultJsonProtocol._

import io.getquill._
import io.getquill.context._

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.store.{Store, StoreDB}

import io.syspulse.skel.explain.{Explain, ExplainScript}
import io.syspulse.skel.explain.server.{ExplainMetaJson, ExplainScriptJson}

// DB-friendly representation: scripts and meta stored as JSON strings
case class Explanation(
  oid: String,
  rid: String,
  scripts: String,   // JSON array string: [{"typ":"js","src":"..."},...]
  name: Option[String],
  description: Option[String],
  sid: Option[String],
  meta: Option[String], // JSON object string or null
  ts0: Long,
  ts: Long
)

class ExplainStoreDB(configuration: Configuration, dbConfigRef: String)
    extends StoreDB[Explain, String](dbConfigRef, "explanation", Some(configuration))
    with ExplainStore {

  lazy private val log = Logger(getClass)
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  def id: String = "db"

  import ctx._
  lazy protected val table = dynamicQuerySchema[Explanation](tableName)

  private def toDb(r: Explain): Explanation = {
    implicit val fmt = ExplainScriptJson.jsonFormat
    Explanation(
      oidKey(r.oid), r.rid, r.scripts.toJson.compactPrint, r.name, r.desc, r.sid,
      r.meta.map(m => m.toJson(ExplainMetaJson.mapFormat).compactPrint),
      r.ts0, r.ts
    )
  }

  private def fromDb(r: Explanation): Explain = {
    implicit val fmt = ExplainScriptJson.jsonFormat
    Explain(
      oid = if(r.oid.isEmpty) None else Some(r.oid),
      rid = r.rid,
      scripts = r.scripts.parseJson.convertTo[Seq[ExplainScript]],
      name = r.name,
      desc = r.description,
      sid = r.sid,
      meta = r.meta.filter(_.nonEmpty).map(_.parseJson.convertTo[Map[String, Any]](ExplainMetaJson.mapFormat)),
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
        description TEXT,
        sid VARCHAR(128),

        meta TEXT,
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
        description TEXT,
        sid VARCHAR(128),

        meta TEXT,
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

  def all: Future[Seq[Explain]] =
    Future.successful(ctx.run(query[Explanation]).map(fromDb))

  def findByOid(oid: Option[String]): Future[Seq[Explain]] =
    Future.successful(ctx.run(query[Explanation].filter(r => r.oid == lift(oidKey(oid)))).map(fromDb))

  def get(oid: Option[String], rid: String): Future[Explain] =
    Future.fromTry(Try {
      ctx.run(query[Explanation].filter(r => r.oid == lift(oidKey(oid)) && r.rid == lift(rid))) match {
        case h :: _ => fromDb(h)
        case Nil    => throw new Exception(s"not found: '$oid/$rid'")
      }
    })

  def +(r: Explain): Future[Explain] = {
    val dbRule = toDb(r)
    Future.fromTry(Try {
      val q = quote {
        query[Explanation].insertValue(lift(dbRule)).onConflictUpdate(_.oid, _.rid)(
          (t, e) => t.scripts -> e.scripts,
          (t, e) => t.name -> e.name,
          (t, e) => t.description -> e.description,
          (t, e) => t.sid -> e.sid,
          (t, e) => t.meta -> e.meta,
          (t, e) => t.ts -> e.ts
        )
      }
      ctx.run(q)
      r
    }.recoverWith { case e => Failure(new Exception(s"could not upsert: $r.oid/${r.rid}", e)) })
  }

  def del(oid: Option[String], rid: String): Future[Explain] =
    get(oid, rid).flatMap { r =>
      Future.fromTry(Try {
        ctx.run(query[Explanation].filter(e => e.oid == lift(oidKey(oid)) && e.rid == lift(rid)).delete)
        r
      })
    }

  def delByOid(oid: Option[String]): Future[Seq[Explain]] = {
    val existingFut = findByOid(oid)
    existingFut.flatMap { existing =>
      Future.fromTry(Try {
        ctx.run(query[Explanation].filter(r => r.oid == lift(oidKey(oid))).delete)
        existing
      }.recoverWith { case e => Failure(new Exception(s"could not delete rules for oid: '$oid'", e)) })
    }
  }

  override def size: Future[Long] = super.size
}
