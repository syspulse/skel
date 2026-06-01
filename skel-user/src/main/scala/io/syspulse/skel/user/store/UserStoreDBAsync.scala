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

// Postgres does not support table name 'user' !
class UserStoreDBAsync(configuration: Configuration, dbConfigRef: String)
    extends StoreDBAsync[User, UUID](dbConfigRef, "users", Some(configuration))
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
      val f1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      val r1 = Await.result(f1, FiniteDuration(10000L, TimeUnit.MILLISECONDS))
      log.info(s"table: ${tableName}: ${r1}")

      val f2 = ctx.executeAction(CREATE_INDEX_SQL)(ExecutionInfo.unknown, ())
      val r2 = Await.result(f2, FiniteDuration(10000L, TimeUnit.MILLISECONDS))
      log.info(s"index: ${indexUserName}: ${r2}")

      Success(r1)
    } catch {
      case e: Exception =>
        log.warn(s"failed to create: ${e.getMessage()}")
        Failure(e)
    }
  }

  override def allAsync: Future[Seq[User]] = ctx.run(users).map(_.map(fromDb))

  override def +!(user: User): Future[User] = {
    log.info(s"INSERT: ${user}")
    val row = toDb(user)
    val q = quote { users.insertValue(lift(row)) }
    ctx.run(q).map(_ => user)
  }

  def updateAsync(id: UUID, req: UserUpdateReq): Future[User] = {
    for {
      user <- this.?!(id)
      user1 = applyUpdate(user, req)
      _ <- {
        log.info(s"UPDATE: ${user1}")
        delAsync(id).flatMap(_ => +!(user1))
      }
    } yield user1
  }

  override def delAsync(id: UUID): Future[UUID] = {
    log.info(s"DELETE: id=${id}")
    val q = quote { users.filter(_.id == lift(id)).delete }
    ctx.run(q).map(r =>
      r match {
        case 0 => throw new Exception(s"not found: ${id}")
        case _ => id
      },
    )
  }

  override def ?!(id: UUID): Future[User] = {
    log.info(s"SELECT: id=${id}")
    ctx.run(users.filter(o => o.id == lift(id))).map(r =>
      r.headOption.map(fromDb) match {
        case Some(u) => u
        case None    => throw new Exception(s"user not found: ${id}")
      },
    )
  }

  def findByXidAsync(xid: String): Future[User] = {
    log.info(s"FIND: xid=${xid}")
    ctx.run(users.filter(o => o.xid.contains(lift(xid)))).map(r =>
      r.headOption.map(fromDb) match {
        case Some(u) => u
        case None    => throw new Exception(s"user not found: ${xid}")
      },
    )
  }

  def findByEmailAsync(email: String): Future[User] = {
    log.info(s"FIND: email=${email}")
    ctx.run(users.filter(o => o.email == lift(email.toLowerCase))).map(r =>
      r.headOption.map(fromDb) match {
        case Some(u) => u
        case None    => throw new Exception(s"user not found: ${email}")
      },
    )
  }

  def +(user: User): Try[User] = Store.fromFuture(this.+!(user))
  def del(id: UUID): Try[UUID] = Store.fromFuture(this.delAsync(id))
  def ?(id: UUID): Try[User] = Store.fromFuture(this.?!(id))
  def all: Seq[User] = Await.result(this.allAsync, FiniteDuration(15000L, TimeUnit.MILLISECONDS))
  def size: Long = Await.result(this.sizeAsync, FiniteDuration(15000L, TimeUnit.MILLISECONDS))
  def findByXid(xid: String): Option[User] = Store.fromFuture(this.findByXidAsync(xid)).toOption
  def findByEmail(email: String): Option[User] = Store.fromFuture(this.findByEmailAsync(email)).toOption
  def update(id: UUID, req: UserUpdateReq): Try[User] =
    Store.fromFuture(this.updateAsync(id, req))
}
