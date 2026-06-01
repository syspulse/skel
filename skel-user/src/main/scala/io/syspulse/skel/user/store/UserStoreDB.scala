package io.syspulse.skel.user.store

import scala.util.{Try, Success, Failure}
import scala.concurrent.Future

import io.jvm.uuid._

import io.getquill._
import io.getquill.context._

import com.typesafe.scalalogging.Logger

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store, StoreDB}

import io.syspulse.skel.user.User
import io.syspulse.skel.user.server.{UserUpdateReq}
import io.syspulse.skel.service.JsonMap
import io.syspulse.skel.ErrNotFound

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
    extends StoreDB[User, UUID](dbConfigRef, "users", Some(configuration))
    with UserStore {

  private val log = Logger(getClass)
  import ctx._

  private val users = quote { querySchema[UserDb]("users") }

  def indexUserName = "user_name"

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
        meta TEXT
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql"    => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }

    try {
      val r1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: ${tableName}: ${r1}")
      val r2 = ctx.executeAction(CREATE_INDEX_SQL)(ExecutionInfo.unknown, ())
      log.info(s"index: ${indexUserName}: ${r2}")
      Success(r1)
    } catch {
      case e: Exception =>
        log.warn(s"failed to create: ${e.getMessage()}")
        Failure(e)
    }
  }

  def all: Seq[User] = ctx.run(users).map(fromDb)

  private def queryPaged(from: Long, size: Long): Seq[User] = {
    val offset = from.max(0L)
    val limit = size.max(0L)
    ctx.run(quote {
      infix"SELECT id, email, name, xid, avatar, ts0, ts, meta FROM users LIMIT ${lift(limit)} OFFSET ${lift(offset)}"
        .as[Query[UserDb]]
    }).map(fromDb)
  }

  override def ??(from: Long, size: Long): Seq[User] = queryPaged(from, size)

  def +(user: User): Try[User] = {
    log.info(s"INSERT: ${user}")
    try {
      val row = toDb(user)
      val q = quote { users.insertValue(lift(row)) }
      ctx.run(q)
      Success(user)
    } catch {
      case e: Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def update(id: UUID, req: UserUpdateReq): Try[User] = {
    this.?(id) match {
      case Success(user) =>
        val user1 = applyUpdate(user, req)
        log.info(s"UPDATE: ${user1}")
        del(id).flatMap(_ => this.+(user1))
      case f => f
    }
  }

  def del(id: UUID): Try[UUID] = {
    log.info(s"DELETE: id=${id}")
    try {
      val q = quote { users.filter(_.id == lift(id)).delete }
      ctx.run(q) match {
        case 0 => Failure(new ErrNotFound(s"${id}"))
        case _ => Success(id)
      }
    } catch {
      case e: Exception => Failure(new Exception(s"could not delete: ${e}"))
    }
  }

  def ?(id: UUID): Try[User] = {
    log.info(s"SELECT: id=${id}")
    try {
      ctx.run(users.filter(o => o.id == lift(id))).map(fromDb) match {
        case h :: _ => Success(h)
        case Nil    => Failure(new ErrNotFound(s"not found: ${id}"))
      }
    } catch {
      case e: Exception => Failure(e)
    }
  }

  def findByXid(xid: String): Option[User] = {
    log.info(s"FIND: xid=${xid}")
    ctx.run(users.filter(o => o.xid.contains(lift(xid)))).map(fromDb) match {
      case h :: _ => Some(h)
      case Nil    => None
    }
  }

  def findByEmail(email: String): Option[User] = {
    log.info(s"FIND: email=${email}")
    ctx.run(users.filter(o => o.email == lift(email.toLowerCase))).map(fromDb) match {
      case h :: _ => Some(h)
      case Nil    => None
    }
  }

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

  override def sizeAsync: Future[Long] = Future { size }
  override def allAsync: Future[Seq[User]] = Future { all }
  override def pageAsync(from: Long, size: Long): Future[Seq[User]] = Future { queryPaged(from, size) }
  override def +!(user: User): Future[User] = Future { this.+(user).get }
  def updateAsync(id: UUID, req: UserUpdateReq): Future[User] = Future {
    update(id, req).get
  }
  override def delAsync(id: UUID): Future[UUID] = Future { del(id).get }
  override def ?!(id: UUID): Future[User] = Future { this.?(id).get }
  def findByXidAsync(xid: String): Future[User] = Future { findByXid(xid).get }
  def findByEmailAsync(email: String): Future[User] = Future { findByEmail(email).get }
}
