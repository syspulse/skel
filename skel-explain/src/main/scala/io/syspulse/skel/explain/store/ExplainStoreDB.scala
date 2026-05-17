package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import com.typesafe.scalalogging.Logger

import spray.json._
import DefaultJsonProtocol._

import io.getquill._
import io.getquill.context._

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.store.{Store, StoreDB}

import io.syspulse.skel.explain.{ExplainRule, ScriptDef}

// DB-friendly representation: scripts stored as JSON array string
case class DBExplainRule(
  oid: String,
  rid: String,
  scripts: String,   // JSON array string: [{"typ":"js","src":"..."},...]
  name: Option[String],
  ts: Long
)

class ExplainStoreDB(configuration: Configuration, dbConfigRef: String)
    extends StoreDB[ExplainRule, String](dbConfigRef, "explain_rule", Some(configuration))
    with ExplainStore {

  lazy private val log = Logger(getClass)

  def id: String = "db"

  import ctx._
  lazy protected val table = dynamicQuerySchema[DBExplainRule](tableName)

  private def toDb(r: ExplainRule): DBExplainRule = {
    implicit val fmt = ScriptDef.jsonFormat
    DBExplainRule(r.oid, r.rid, r.scripts.toJson.compactPrint, r.name, r.ts)
  }

  private def fromDb(r: DBExplainRule): ExplainRule = {
    implicit val fmt = ScriptDef.jsonFormat
    ExplainRule(r.oid, r.rid, r.scripts.parseJson.convertTo[Seq[ScriptDef]], r.name, r.ts)
  }

  def create: Try[Long] = {
    val CREATE_TABLE_MYSQL_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        oid VARCHAR(255) NOT NULL,
        rid VARCHAR(255) NOT NULL,
        scripts TEXT,
        name VARCHAR(255),
        ts BIGINT,
        PRIMARY KEY (oid, rid)
      );"""

    val CREATE_TABLE_POSTGRES_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        oid VARCHAR(255) NOT NULL,
        rid VARCHAR(255) NOT NULL,
        scripts TEXT,
        name VARCHAR(255),
        ts BIGINT,
        PRIMARY KEY (oid, rid)
      );"""

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql"    => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }

    try {
      log.info(s"table: '${tableName}': ${getDbType}: '${CREATE_TABLE_SQL.replaceAll("\\s+", " ")}'")
      val r = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: '${tableName}': created: ${r}")
      Success(r)
    } catch {
      case e: Exception =>
        log.error(s"failed to create table: '${tableName}': ${e.getMessage()}")
        Failure(e)
    }
  }

  def all: Seq[ExplainRule] =
    ctx.run(query[DBExplainRule]).map(fromDb)

  def findByOid(oid: String): Seq[ExplainRule] =
    ctx.run(query[DBExplainRule].filter(r => r.oid == lift(oid))).map(fromDb)

  def get(oid: String, rid: String): Try[ExplainRule] =
    ctx.run(query[DBExplainRule].filter(r => r.oid == lift(oid) && r.rid == lift(rid))) match {
      case h :: _ => Success(fromDb(h))
      case Nil    => Failure(new Exception(s"not found: '$oid/$rid'"))
    }

  def +(r: ExplainRule): Try[ExplainRule] = {
    val dbRule = toDb(r)
    try {
      val q = quote {
        query[DBExplainRule].insertValue(lift(dbRule)).onConflictUpdate(_.oid, _.rid)(
          (t, e) => t.scripts -> e.scripts,
          (t, e) => t.name -> e.name,
          (t, e) => t.ts -> e.ts
        )
      }
      ctx.run(q)
      Success(r)
    } catch {
      case e: Exception => Failure(new Exception(s"could not upsert: ${r.oid}/${r.rid}", e))
    }
  }

  def del(oid: String, rid: String): Try[ExplainRule] =
    get(oid, rid).flatMap { r =>
      try {
        ctx.run(query[DBExplainRule].filter(e => e.oid == lift(oid) && e.rid == lift(rid)).delete)
        Success(r)
      } catch {
        case e: Exception => Failure(e)
      }
    }

  override def size: Long = super.size
}
