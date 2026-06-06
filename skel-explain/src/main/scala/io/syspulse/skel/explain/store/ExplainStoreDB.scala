package io.syspulse.skel.explain.store

import scala.util.{Failure, Success, Try}
import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

import spray.json._
import DefaultJsonProtocol._

import io.getquill._
import io.getquill.context._

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.store.{Store, StoreDB, StoreDBAsync, StoreFts, StoreSearch}

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

class ExplainStoreDB(configuration: Configuration, dbConfigRef: String, searchIndexes: Option[Set[String]] = None)
    extends StoreDBAsync[Explain, String](dbConfigRef, "explanation", Some(configuration), searchIndexes)
    with ExplainStore {

  private def searchFields = Seq("name", "description")

  lazy private val log = Logger(getClass)

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

  def indexes = Set(
    ("explain_oid", Set("oid")),
    ("explain_rid", Set("rid"))
  )
  def indexExplainFts = "explain_fts"
  def colExplainTsv = "tsv"

  def create: Try[Long] = {
    val CREATE_INDEX_MYSQL_SQL = indexes.map(idx => s"CREATE INDEX ${idx._1} ON ${tableName} (${idx._2.mkString(",")});")
    val CREATE_INDEX_POSTGRES_SQL = indexes.map(idx => s"CREATE INDEX IF NOT EXISTS ${idx._1} ON ${tableName} (${idx._2.mkString(",")});")

    val CREATE_INDEX_SQL = getDbType match {
      case "mysql"    => CREATE_INDEX_MYSQL_SQL
      case "postgres" => CREATE_INDEX_POSTGRES_SQL
    }

    val tsvExprPostgres = StoreFts.pgTsvExpr(searchFields)
    val tsvColDef = postgresTsvColumnDef(colExplainTsv, tsvExprPostgres)

    val CREATE_INDEX_FTS_MYSQL_SQL =
      s"CREATE FULLTEXT INDEX ${indexExplainFts} ON ${tableName} (${searchFields.mkString(", ")});"

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
      );
      """

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
        $tsvColDef
        PRIMARY KEY (oid, rid)
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql"    => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }

    try {
      val f1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      val r1 = Await.result(f1, FiniteDuration(timeout, TimeUnit.MILLISECONDS))
      log.info(s"table: ${tableName}: ${r1}")

      CREATE_INDEX_SQL.zip(indexes).foreach { case (idx, idxDef) =>
        val f2 = ctx.executeAction(idx)(ExecutionInfo.unknown, ())
        val r2 = Await.result(f2, FiniteDuration(timeout, TimeUnit.MILLISECONDS))
        log.info(s"index: ${idxDef._1}: ${r2}")
      }

      getDbType match {
        case "postgres" =>
          setupPostgresSearchIndexes(colExplainTsv, indexExplainFts, tsvExprPostgres, "explain", searchFields)

        case "mysql" =>
          if (StoreSearch.hasFts(searchIndexes)) {
            try {
              val f3 = ctx.executeAction(CREATE_INDEX_FTS_MYSQL_SQL)(ExecutionInfo.unknown, ())
              Await.result(f3, FiniteDuration(timeout, TimeUnit.MILLISECONDS))
            } catch {
              case _: Exception => ()
            }
          }

        case _ => ()
      }

      Success(r1)
    } catch {
      case e: Exception =>
        log.warn(s"failed to create: ${e.getMessage()}")
        Failure(e)
    }
  }

  def all: Future[Seq[Explain]] =
    ctx.run(query[Explanation]).map(_.map(fromDb))

  def findByOid(oid: Option[String]): Future[Seq[Explain]] =
    ctx.run(query[Explanation].filter(r => r.oid == lift(oidKey(oid)))).map(_.map(fromDb))

  def get(oid: Option[String], rid: String): Future[Explain] =
    ctx
      .run(query[Explanation].filter(r => r.oid == lift(oidKey(oid)) && r.rid == lift(rid)))
      .map(
        _.map(fromDb)
          .headOption.getOrElse(throw new Exception(s"not found: '$oid/$rid'"))
      )

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

  override def size: Future[Long] =
    queryCount(s"SELECT count(*) FROM $tableName")

  private def buildWhere(oid: Option[String], rid: Option[String]): String = {
    val conds = Seq(
      oid.map(o => s"oid = '${sqlLit(oidKey(Option(o).filter(_.nonEmpty)))}'"),
      rid.map(r => s"rid = '${sqlLit(r)}'"),
    ).flatten
    if (conds.isEmpty) "" else s"WHERE ${conds.mkString(" AND ")}"
  }

  private def queryPaged(oid: Option[String], rid: Option[String], from: Long, size: Long): Future[Seq[Explain]] = {
    val where = buildWhere(oid, rid)
    val off = pageInt(from)
    val lim = pageInt(size)
    val sql =
      s"SELECT $selectCols FROM $tableName $where LIMIT $lim OFFSET $off"
    querySql(sql).map(_.map(fromDb))
  }

  private def queryFiltered(oid: Option[String], rid: Option[String]): Future[Seq[Explain]] = {
    val where = buildWhere(oid, rid)
    val sql = s"SELECT $selectCols FROM $tableName $where"
    querySql(sql).map(_.map(fromDb))
  }

  private def queryCountFiltered(oid: Option[String], rid: Option[String]): Future[Long] = {
    val where = buildWhere(oid, rid)
    queryCount(s"SELECT count(*) FROM $tableName $where")
  }

  override def ???(from: Long, size: Long)(implicit ec: ExecutionContext): Future[Seq[Explain]] =
    queryPaged(None, None, from, size)

  override def list(oid: Option[String] = None, rid: Option[String] = None, from: Option[Long] = None, size: Option[Long] = None)(implicit ec: ExecutionContext): Future[ExplainStore.Page] =
    (from, size) match {
      case (Some(_), None) | (None, Some(_)) =>
        Future.failed(new IllegalArgumentException("from and size must both be set for paging"))
      case (Some(f), Some(s)) =>
        queryCountFiltered(oid, rid).flatMap { total =>
          queryPaged(oid, rid, f, s).map(rules => ExplainStore.Page(rules, total))
        }
      case _ =>
        queryCountFiltered(oid, rid).flatMap { total =>
          queryFiltered(oid, rid).map(rules => ExplainStore.Page(rules, total))
        }
    }

  private def pageInt(n: Long): Int =
    n.max(0L).min(Int.MaxValue.toLong).toInt

  private def sqlLit(s: String): String =
    s.replace("'", "''")

  private val selectCols =
    "oid, rid, scripts, name, description, sid, meta, ts0, ts"

  private def rowToExplanation(row: com.github.jasync.sql.db.RowData, unused: Unit): Explanation = {
    def optStr(i: Int): Option[String] = {
      val v = row.getString(i)
      if (v == null) None else Some(v)
    }
    Explanation(
      oid = row.getAs[String](0),
      rid = row.getAs[String](1),
      scripts = row.getAs[String](2),
      name = optStr(3),
      description = optStr(4),
      sid = optStr(5),
      meta = optStr(6),
      ts0 = row.getAs[Long](7),
      ts = row.getAs[Long](8),
    )
  }

  private def querySql(sql: String): Future[Seq[Explanation]] =
    ctx.executeQuery(sql, extractor = rowToExplanation)(ExecutionInfo.unknown, ())

  private def queryCount(sql: String): Future[Long] = {
    def rowToLong(row: com.github.jasync.sql.db.RowData, unused: Unit): Long = row.getAs[Long](0)
    ctx.executeQuerySingle(sql, extractor = rowToLong)(ExecutionInfo.unknown, ())
  }

  def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[ExplainStore.Page] = {
    log.info(s"SEARCH: query=${query}, from=${from}, size=${size}")
    val q = ExplainStore.normalizeSearchQuery(query)
    if (q.length < ExplainStore.SEARCH_MIN_LEN) return Future.successful(ExplainStore.Page(Seq.empty, 0))

    val offset = from.getOrElse(0L).max(0L)
    val limit = size.map(_.max(0L))

    def pageResult(rulesF: Future[Seq[Explain]], totalF: Future[Long]): Future[ExplainStore.Page] =
      rulesF.flatMap(rules => totalF.map(total => ExplainStore.Page(rules, total)))

    getDbType match {
      case "postgres" =>
        val tsvCol = if (StoreSearch.hasFts(searchIndexes)) Some(colExplainTsv) else None
        StoreFts.postgresSearchWhere(searchIndexes, tsvCol, searchFields, q, sqlLit) match {
          case None => Future.successful(ExplainStore.Page(Seq.empty, 0))
          case Some(where) =>
            val totalF = queryCount(s"SELECT count(*) FROM $tableName WHERE $where")
            val rulesF = limit match {
              case Some(l) =>
                val off = pageInt(offset)
                val lim = pageInt(l)
                val sql =
                  s"""SELECT $selectCols FROM $tableName
                     |WHERE $where
                     |LIMIT $lim OFFSET $off""".stripMargin
                querySql(sql).map(_.map(fromDb))
              case None =>
                val sql =
                  s"""SELECT $selectCols FROM $tableName
                     |WHERE $where""".stripMargin
                querySql(sql).map(_.map(fromDb))
            }
            pageResult(rulesF, totalF)
        }

      case "mysql" =>
        if (!StoreSearch.hasFts(searchIndexes))
          return Future.successful(ExplainStore.Page(Seq.empty, 0))
        val totalF = queryCount(
          s"""SELECT count(*) FROM $tableName
             |WHERE MATCH(name, description) AGAINST ('${sqlLit(q)}' IN NATURAL LANGUAGE MODE)""".stripMargin,
        )
        val rulesF = limit match {
          case Some(l) =>
            val off = pageInt(offset)
            val lim = pageInt(l)
            val sql =
              s"""SELECT $selectCols FROM $tableName
                 |WHERE MATCH(name, description) AGAINST ('${sqlLit(q)}' IN NATURAL LANGUAGE MODE)
                 |LIMIT $lim OFFSET $off""".stripMargin
            querySql(sql).map(_.map(fromDb))
          case None =>
            val sql =
              s"""SELECT $selectCols FROM $tableName
                 |WHERE MATCH(name, description) AGAINST ('${sqlLit(q)}' IN NATURAL LANGUAGE MODE)""".stripMargin
            querySql(sql).map(_.map(fromDb))
        }
        pageResult(rulesF, totalF)

      case _ =>
        val like = s"%${q.toLowerCase}%"
        val rowsF = ctx.run(
          table.filter(r =>
            r.name.exists(_.toLowerCase.like(lift(like))) ||
              r.description.exists(_.toLowerCase.like(lift(like))),
          ),
        )
        rowsF.map { rows =>
          val all = rows.map(fromDb)
          val total = all.size.toLong
          val pageRules = limit match {
            case Some(l) => page(all, offset, l)
            case None    => all
          }
          ExplainStore.Page(pageRules, total)
        }
    }
  }
}
