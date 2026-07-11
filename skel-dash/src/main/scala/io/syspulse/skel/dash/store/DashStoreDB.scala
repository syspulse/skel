package io.syspulse.skel.dash.store

import scala.util.{Failure,Success,Try}
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.Logger
import io.jvm.uuid.UUID
import java.time.LocalDateTime

import spray.json._

import io.getquill._
import io.getquill.context._

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB}

import io.syspulse.skel.dash.server._
import io.syspulse.skel.dash.Dash
import io.syspulse.skel.dash.server.DashJson._

class DashStoreDB(configuration:Configuration,dbConfigRef:String)
  extends StoreDB[Dash,String](dbConfigRef,"dash",Some(configuration))
  with DashStore {

  lazy private val log = Logger(getClass)

  def id:String = "db"

  import ctx._
  lazy protected  val table = dynamicQuerySchema[Dash](tableName)

  def indexPid = "dash_pid"
  def indexTid = "dash_tid"

  def update():Try[Long] = {
    val UPDATE_TABLE_POSTGRES_SQL = s"""ALTER TABLE ${tableName} ADD COLUMN icon VARCHAR(250) DEFAULT NULL;"""
    val UPDATE_TABLE_MYSQL_SQL = s"""ALTER TABLE ${tableName} ADD COLUMN icon VARCHAR(250) DEFAULT NULL;"""

    val UPDATE_TABLE_SQL = getDbType match {
      case "mysql" => UPDATE_TABLE_MYSQL_SQL
      case "postgres" => UPDATE_TABLE_POSTGRES_SQL
    }

    try {
      val r = ctx.executeAction(UPDATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: '${tableName}': updated: ${r}")
      Success(r)
    } catch {
      case e:Exception => {
        log.error(s"failed to update table: '${tableName}': ${e.getMessage()}")
        Failure(e)
      }
    }
  }

  // ATTENTION: called from constructor, so derived class vals are not initialized yet !
  def create:Try[Long] = {
    val CREATE_INDEX_PID_MYSQL_SQL = s"CREATE INDEX ${indexPid} ON ${tableName} (pid);"
    val CREATE_INDEX_PID_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexPid} ON ${tableName} (pid);"
    val CREATE_INDEX_TID_MYSQL_SQL = s"CREATE INDEX ${indexTid} ON ${tableName} (tid);"
    val CREATE_INDEX_TID_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexTid} ON ${tableName} (tid);"

    val CREATE_INDEX_PID_SQL = getDbType match {
      case "mysql" => CREATE_INDEX_PID_MYSQL_SQL
      case "postgres" => CREATE_INDEX_PID_POSTGRES_SQL
    }

    val CREATE_INDEX_TID_SQL = getDbType match {
      case "mysql" => CREATE_INDEX_TID_MYSQL_SQL
      case "postgres" => CREATE_INDEX_TID_POSTGRES_SQL
    }

    // ATTENTION: 1MB Blob VARCHAR
    val CREATE_TABLE_MYSQL_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY,
        layout VARCHAR(65000),
        name VARCHAR(250),
        info VARCHAR(1024),
        tags VARCHAR(200),
        pid VARCHAR(36),
        tid VARCHAR(36),
        ts BIGINT,
        ts0 BIGINT,
        status INT,
        icon VARCHAR(250) DEFAULT NULL
      );
      """

    val CREATE_TABLE_POSTGRES_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY,
        layout VARCHAR(65000),
        name VARCHAR(250),
        info VARCHAR(1024),
        tags VARCHAR(200),
        pid VARCHAR(36),
        tid VARCHAR(36),
        ts BIGINT,
        ts0 BIGINT,
        status INT,
        icon VARCHAR(250) DEFAULT NULL
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql" => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }

    val r1 = try {
      log.info(s"table: '${tableName}': ${getDbType}: '${CREATE_TABLE_SQL.replaceAll("\\s+"," ")}'")
      val r1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: '${tableName}': created: ${r1}")
      Success(r1)
    } catch {
      case e:Exception => {
        // short name without full stack (change to check for duplicate index)
        // remove ERROR to avpid kubernetes treating it as ERROR
        log.error(s"failed to create table: '${tableName}': ${e.getMessage()}")
        Failure(e)
      }
    }

    createIndex(indexPid,CREATE_INDEX_PID_SQL)
    createIndex(indexTid,CREATE_INDEX_TID_SQL)

    update()

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

  // if conversion is needed
  def toDash(d:Dash):Dash = {
    d
  }

  def fromDash(d:Dash):Dash = {
    d
  }

  def all(tid:Option[String],pid:Option[String]):Future[Seq[Dash]] = {
    Future.successful {
      val rr =
        if(pid.isDefined && tid.isDefined)
          ctx.run(query[Dash].filter(ad => ad.pid == lift(pid) && ad.tid == lift(tid)))
        else if(pid.isDefined)
          ctx.run(query[Dash].filter(ad => ad.pid == lift(pid)))
        else if(tid.isDefined)
          ctx.run(query[Dash].filter(ad => ad.tid == lift(tid)))
        else
          ctx.run(query[Dash])

      log.debug(s"all: pid=${tid}/${pid}: ${rr}")
      rr.map(r => toDash(r))
    }
  }

  def ??(id:String):Future[Option[Dash]] = ???(id,None,None).map(Some(_)).recover { case _ => None }

  // ATTENTION: tid is currently not used, since pid is unique !
  def ???(id:String,tid:Option[String],pid:Option[String]): Future[Dash] = {
    Future.fromTry(Try {
      log.debug(s"SELECT: ${tid}/${pid}/${id}")
      val rr = if(pid.isDefined)
        ctx.run(query[Dash].filter(w => w.id == lift(id) && w.pid == lift(pid)))
      else
        ctx.run(query[Dash].filter(w => w.id == lift(id)))

      rr match {
        case h :: _ => toDash(h)
        case Nil => throw new Exception(s"not found: '${pid}/${id}'")
      }
    })
  }

  val deleteById = quote { (id:String,pid:Option[String]) =>
    query[Dash].filter(o => o.id == id && o.pid == pid).delete
  }

  def +++(ad:Dash):Future[Dash] = {
    Future.fromTry(Try {
      log.info(s"UPSERT: ${ad.tid}/${ad.pid}/${ad.id}")
      val q = quote {
        query[Dash].insertValue(lift(ad)).onConflictUpdate(_.id)(
          (t, e) => t.layout -> e.layout,
          (t, e) => t.name -> e.name,
          (t, e) => t.info -> e.info,
          (t, e) => t.tags -> e.tags,
          (t, e) => t.pid -> e.pid,
          (t, e) => t.tid -> e.tid,
          (t, e) => t.ts -> e.ts,
          (t, e) => t.ts0 -> e.ts0,
          (t, e) => t.icon -> e.icon
        )
      }
      val r = ctx.run(q)
      log.info(s"UPSERT: ${r}")
      ad
    })
  }

  def +(c:Dash):Future[Dash] = {
    val ad = fromDash(c)
    +++(ad).map(_ => c)
  }

  // ATTENTION: tid is currently not used, since pid is unique !
  def del(id:String,tid:Option[String],pid:Option[String]):Future[String] = {
    Future.fromTry(Try {
      log.info(s"DELETE: ${tid}/${pid}/${id}")
      ctx.run(deleteById(lift(id),lift(pid))) match {
        case 0 => throw new Exception(s"not found: ${pid}/${id}")
        case _ => id
      }
    })
  }

  def size(tid:Option[String],pid:Option[String]):Future[Long] = {
    Future.successful {
      if(pid.isDefined && tid.isDefined) {
        ctx.run(query[Dash].filter(p => p.pid == lift(pid) && p.tid == lift(tid)).size)
      }
      else if(pid.isDefined) {
        ctx.run(query[Dash].filter(p => p.pid == lift(pid)).size)
        ctx.run(totalSQL())
      }
      else if(tid.isDefined) {
        ctx.run(query[Dash].filter(p => p.tid == lift(tid)).size)
        ctx.run(totalSQL())
      } else {
        ctx.run(totalSQL())
      }
    }
  }

  override def size:Future[Long] = super.size

}
