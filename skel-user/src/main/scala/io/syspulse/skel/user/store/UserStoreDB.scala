package io.syspulse.skel.user.store

import scala.util.Try
import scala.util.{Success, Failure}

import io.jvm.uuid._

import io.getquill._
import io.getquill.context._

import com.typesafe.scalalogging.Logger

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store, StoreDB, StoreDBAsync}

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.{UserUpdateReq}
import io.syspulse.skel.service.JsonMap
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

/** DB row — `meta` stored as JSON text. */
case class UserDb(
  id: UUID,
  email: String,
  name: Option[String],
  xid: Option[String],
  avatar: Option[String],
  ts0: Long,
  ts: Long,
  meta: Option[String],
)

// Postgres does not support table name 'user' !
class UserStoreDB(configuration: Configuration, dbConfigRef: String)
    extends StoreDBAsync[User, UUID](dbConfigRef, "users", Some(configuration))
    with UserStore {

  private lazy val log = Logger(getClass)
  import ctx._

  private val users = quote { querySchema[UserDb]("users") }

  def indexUserName = "user_name"
  def indexUserFts = "user_fts"
  def colUserTsv = "tsv"

  private def toDb(u: User): UserDb =
    UserDb(
      id = u.id,
      email = u.email.toLowerCase,
      name = u.name,
      xid = u.xid,
      avatar = u.avatar,
      ts0 = u.ts0,
      ts = u.ts,
      meta = u.meta.map(m => m.toJson(JsonMap.mapFormat).compactPrint),
    )

  private def fromDb(r: UserDb): User =
    User(
      id = r.id,
      email = r.email,
      name = r.name,
      xid = r.xid,
      avatar = r.avatar,
      ts0 = r.ts0,
      ts = r.ts,
      meta = r.meta.filter(_.nonEmpty).map(_.parseJson.convertTo[Map[String, Any]](JsonMap.mapFormat)),
    )

  def create: Try[Long] = {
    val CREATE_INDEX_MYSQL_SQL = s"CREATE INDEX ${indexUserName} ON ${tableName} (name);"
    val CREATE_INDEX_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexUserName} ON ${tableName} (name);"

    val CREATE_INDEX_SQL = getDbType match {
      case "mysql"    => CREATE_INDEX_MYSQL_SQL
      case "postgres" => CREATE_INDEX_POSTGRES_SQL
    }

    // Free-text search:
    // - Postgres: stored generated tsvector + GIN index
    //   Split email/name/xid on non-alphanumerics so "demo" matches demo-xxx@example.com
    // - MySQL: FULLTEXT index (best-effort)
    val tsvExprPostgres =
      """regexp_replace(coalesce(email, ''), '[^a-zA-Z0-9]+', ' ', 'g') || ' ' ||
        |regexp_replace(coalesce(name, ''), '[^a-zA-Z0-9]+', ' ', 'g') || ' ' ||
        |regexp_replace(coalesce(xid, ''), '[^a-zA-Z0-9]+', ' ', 'g')""".stripMargin

    val CREATE_INDEX_FTS_POSTGRES_SQL =
      s"CREATE INDEX IF NOT EXISTS ${indexUserFts} ON ${tableName} USING GIN (${colUserTsv});"

    val CREATE_INDEX_FTS_MYSQL_SQL =
      s"CREATE FULLTEXT INDEX ${indexUserFts} ON ${tableName} (email, name, xid);"

    val CREATE_TABLE_MYSQL_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY,
        email VARCHAR(255) NOT NULL,
        name VARCHAR(255),
        xid VARCHAR(255),
        avatar VARCHAR(255),
        ts0 BIGINT,
        ts BIGINT,
        meta TEXT
      );
      """

    val CREATE_TABLE_POSTGRES_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id UUID PRIMARY KEY,
        email VARCHAR(255) NOT NULL,
        name VARCHAR(255),
        xid VARCHAR(255),
        avatar VARCHAR(255),
        ts0 BIGINT,
        ts BIGINT,
        meta TEXT,
        ${colUserTsv} tsvector GENERATED ALWAYS AS (
          to_tsvector('simple', ${tsvExprPostgres})
        ) STORED
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql"    => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }

    try {
      val f1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      val r1 = Await.result(f1, FiniteDuration(10000L, TimeUnit.MILLISECONDS))
      log.info(s"table: ${tableName}: ${r1}")

      val f2 = ctx.executeAction(CREATE_INDEX_SQL)(ExecutionInfo.unknown, ())
      val r2 = Await.result(f2, FiniteDuration(10000L, TimeUnit.MILLISECONDS))
      log.info(s"index: ${indexUserName}: ${r2}")

      // FTS index (best-effort)
      getDbType match {
        case "postgres" =>
          val f3 = ctx.executeAction(CREATE_INDEX_FTS_POSTGRES_SQL)(ExecutionInfo.unknown, ())
          Await.result(f3, FiniteDuration(10000L, TimeUnit.MILLISECONDS))

        case "mysql" =>
          // MySQL does not support IF NOT EXISTS for FULLTEXT in all versions; ignore "already exists".
          try {
            val f3 = ctx.executeAction(CREATE_INDEX_FTS_MYSQL_SQL)(ExecutionInfo.unknown, ())
            Await.result(f3, FiniteDuration(10000L, TimeUnit.MILLISECONDS))
          } catch {
            case _: Exception => ()
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

  def all: Future[Seq[User]] = ctx.run(users).map(_.map(fromDb))

  /** Jasync/Postgres: avoid Quill `lift` for table names, LIMIT/OFFSET, and FTS args — use plain SQL + `sendQuery`. */
  override def size: Future[Long] =
    queryCount(s"SELECT count(*) FROM $tableName")

  private def pageInt(n: Long): Int =
    n.max(0L).min(Int.MaxValue.toLong).toInt

  private def sqlLit(s: String): String =
    s.replace("'", "''")

  private def queryPaged(from: Long, size: Long): Future[Seq[User]] = {
    val off = pageInt(from)
    val lim = pageInt(size)
    // Postgres/Jasync: LIMIT/OFFSET bind params ($1) fail in infix; use raw SQL without placeholders
    val sql =
      s"SELECT id, email, name, xid, avatar, ts0, ts, meta FROM $tableName LIMIT $lim OFFSET $off"
    querySql(sql).map(_.map(fromDb))
  }

  private def rowToUserDb(row: com.github.jasync.sql.db.RowData, unused: Unit): UserDb = {
    def optStr(i: Int): Option[String] = {
      val v = row.getString(i)
      if (v == null) None else Some(v)
    }
    UserDb(
      id = row.getAs[UUID](0),
      email = row.getAs[String](1),
      name = optStr(2),
      xid = optStr(3),
      avatar = optStr(4),
      ts0 = row.getAs[Long](5),
      ts = row.getAs[Long](6),
      meta = optStr(7),
    )
  }

  private def querySql(sql: String): Future[Seq[UserDb]] = {
    log.debug(s"querySql: $sql")
    ctx.executeQuery(sql, extractor = rowToUserDb)(ExecutionInfo.unknown, ())
  }

  private def queryCount(sql: String): Future[Long] = {
    log.debug(s"queryCount: $sql")
    def rowToLong(row: com.github.jasync.sql.db.RowData, unused: Unit): Long = row.getAs[Long](0)
    ctx.executeQuerySingle(sql, extractor = rowToLong)(ExecutionInfo.unknown, ())
  }

  override def ???(from: Long, size: Long)(implicit ec: ExecutionContext): Future[Seq[User]] =
    queryPaged(from, size)

  override def list(from: Option[Long], size: Option[Long])(implicit ec: ExecutionContext): Future[UserStore.Page] =
    (from, size) match {
      case (Some(f), Some(s)) =>
        this.size.flatMap(total => queryPaged(f, s).map(users => UserStore.Page(users, total)))
      case (None, None) =>
        all.map(users => UserStore.Page(users, users.size.toLong))
      case _ =>
        Future.failed(new IllegalArgumentException("from and size must both be set for paging"))
    }

  def +(user: User): Future[User] = {
    log.info(s"INSERT: ${user}")
    val row = toDb(user)
    val q = quote { users.insertValue(lift(row)) }
    ctx.run(q).map(_ => user)
  }

  def update(id: UUID, req: UserUpdateReq): Future[User] = {
    for {
      user <- this.?(id)
      user1 = applyUpdate(user, req)
      _ <- {
        log.info(s"UPDATE: ${user1}")
        del(id).flatMap(_ => this.+(user1))
      }
    } yield user1
  }

  def del(id: UUID): Future[UUID] = {
    log.info(s"DELETE: id=${id}")
    val q = quote { users.filter(_.id == lift(id)).delete }
    ctx.run(q).map(r =>
      r match {
        case 0 => throw new Exception(s"not found: ${id}")
        case _ => id
      },
    )
  }

  def ?(id: UUID): Future[User] = {
    log.info(s"SELECT: id=${id}")
    ctx.run(users.filter(o => o.id == lift(id))).map(r =>
      r.headOption.map(fromDb) match {
        case Some(u) => u
        case None    => throw new Exception(s"user not found: ${id}")
      },
    )
  }

  def findByXid(xid: String): Future[Option[User]] = {
    log.info(s"FIND: xid=${xid}")
    ctx.run(users.filter(o => o.xid.contains(lift(xid)))).map(r =>
      r.headOption.map(fromDb)
    )
  }

  def findByEmail(email: String): Future[Option[User]] = {
    log.info(s"FIND: email=${email}")
    ctx.run(users.filter(o => o.email == lift(email.toLowerCase))).map(r =>
      r.headOption.map(fromDb)
    )
  }

  def search(query: String, from: Option[Long] = None, size: Option[Long] = None): Future[UserStore.Page] = {
    log.info(s"SEARCH: query=${query}, from=${from}, size=${size}")
    val q = UserStore.normalizeSearchQuery(query)
    if (q.length < UserStore.SEARCH_MIN_LEN) return Future.successful(UserStore.Page(Seq.empty, 0))

    val offset = from.getOrElse(0L).max(0L)
    val limit = size.map(_.max(0L))

    def pageResult(usersF: Future[Seq[User]], totalF: Future[Long]): Future[UserStore.Page] =
      usersF.flatMap(users => totalF.map(total => UserStore.Page(users, total)))

    getDbType match {
      case "postgres" =>
        val totalF = queryCount(
          s"SELECT count(*) FROM $tableName WHERE tsv @@ plainto_tsquery('simple', '${sqlLit(q)}')",
        )
        val usersF = limit match {
          case Some(l) =>
            val off = pageInt(offset)
            val lim = pageInt(l)
            val sql =
              s"""SELECT id, email, name, xid, avatar, ts0, ts, meta FROM $tableName
                 |WHERE tsv @@ plainto_tsquery('simple', '${sqlLit(q)}')
                 |LIMIT $lim OFFSET $off""".stripMargin
            querySql(sql).map(_.map(fromDb))
          case None =>
            val sql =
              s"""SELECT id, email, name, xid, avatar, ts0, ts, meta FROM $tableName
                 |WHERE tsv @@ plainto_tsquery('simple', '${sqlLit(q)}')""".stripMargin
            querySql(sql).map(_.map(fromDb))
        }
        pageResult(usersF, totalF)

      case "mysql" =>
        val totalF = queryCount(
          s"""SELECT count(*) FROM $tableName
             |WHERE MATCH(email, name, xid) AGAINST ('${sqlLit(q)}' IN NATURAL LANGUAGE MODE)""".stripMargin,
        )
        val usersF = limit match {
          case Some(l) =>
            val off = pageInt(offset)
            val lim = pageInt(l)
            val sql =
              s"""SELECT id, email, name, xid, avatar, ts0, ts, meta FROM $tableName
                 |WHERE MATCH(email, name, xid) AGAINST ('${sqlLit(q)}' IN NATURAL LANGUAGE MODE)
                 |LIMIT $lim OFFSET $off""".stripMargin
            querySql(sql).map(_.map(fromDb))
          case None =>
            val sql =
              s"""SELECT id, email, name, xid, avatar, ts0, ts, meta FROM $tableName
                 |WHERE MATCH(email, name, xid) AGAINST ('${sqlLit(q)}' IN NATURAL LANGUAGE MODE)""".stripMargin
            querySql(sql).map(_.map(fromDb))
        }
        pageResult(usersF, totalF)

      case _ =>
        val like = s"%${q.toLowerCase}%"
        val rowsF = ctx.run(
          users.filter(o =>
            o.email.toLowerCase.like(lift(like)) ||
              o.name.exists(_.toLowerCase.like(lift(like))) ||
              o.xid.exists(_.toLowerCase.like(lift(like))),
          ),
        )
        rowsF.map { rows =>
          val all = rows.map(fromDb)
          val total = all.size.toLong
          val pageUsers = limit match {
            case Some(l) => page(all, offset, l)
            case None    => all
          }
          UserStore.Page(pageUsers, total)
        }
    }
  }
}
