package io.syspulse.skel.odometer.store

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._

import io.getquill._
import io.getquill.context._

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB}

import io.syspulse.skel.odometer.Odo

// Postgres does not support table name 'odometer' !
class OdoStoreDB(configuration:Configuration,dbConfigRef:String)
  extends StoreDB[Odo,String](dbConfigRef,"odometers",Some(configuration))
  with OdoStore {
  private val log = Logger(getClass)
  import ctx._

  // Because of Postgres, using dynamic schema to override table name to 'odometers'
  val table = dynamicQuerySchema[Odo](tableName)

  def indexOdoTimestamp = "odometer_ts"

  // ATTENTION: called from constructor, so derived class vals are not initialized yet !
  def create:Try[Long] = {

    val CREATE_INDEX_MYSQL_SQL = s"CREATE INDEX ${indexOdoTimestamp} ON ${tableName} (ts);"
    val CREATE_INDEX_POSTGRES_SQL = s"CREATE INDEX IF NOT EXISTS ${indexOdoTimestamp} ON ${tableName} (ts);"

    val CREATE_INDEX_SQL = getDbType match {
      case "mysql" => CREATE_INDEX_MYSQL_SQL
      case "postgres" => CREATE_INDEX_POSTGRES_SQL
    }

    val CREATE_TABLE_MYSQL_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY,
        v BIGINT,
        ts BIGINT
      );
      """

    val CREATE_TABLE_POSTGRES_SQL =
      s"""CREATE TABLE IF NOT EXISTS ${tableName} (
        id VARCHAR(36) PRIMARY KEY,
        v BIGINT,
        ts BIGINT
      );
      """

    val CREATE_TABLE_SQL = getDbType match {
      case "mysql" => CREATE_TABLE_MYSQL_SQL
      case "postgres" => CREATE_TABLE_POSTGRES_SQL
    }


    try {

      val r1 = ctx.executeAction(CREATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      log.info(s"table: ${tableName}: ${r1}")
      // val r2 = ctx.executeAction(CREATE_INDEX_SQL)(ExecutionInfo.unknown, ())
      // log.info(s"index: ${indexOdoTimestamp}: ${r2}")

      Success(r1)
    } catch {
      case e:Exception => {
        log.warn(s"failed to create: ${e.getMessage()}");
        Failure(e)
      }
    }
  }

  def all:Future[Seq[Odo]] = Future.successful(ctx.run(table))

  val deleteById = (id:String) => table.filter(_.id == lift(id)).delete

  def +(o:Odo):Future[Odo] = {
    log.info(s"INSERT: ${o}")
    try {
      ctx.run(table.insertValue(o))
      Future.successful(o)
    } catch {
      case e:Exception => Future.failed(new Exception(s"could not insert: ${e}"))
    }
  }

  def update(id:String,v:Long):Future[Odo] = {
    this.?(id).flatMap { o =>
      val o1 = modify(o,v)

      log.info(s"UPDATE: ${o1}")
      try {
        val q =
          table
            .filter(o => o.id == lift(id))
            .update(
              set(_.v, quote(lift(o1.v))),
            )

        ctx.run(q)

        Future.successful(o1)

      } catch {
        case e:Exception => Future.failed(new Exception(s"could not update: ${e}"))
      }
    }
  }

  def ++(id:String,delta:Long):Future[Odo] = {
    this.?(id).flatMap { o =>
      val o1 = o.copy(v = o.v + delta)

      log.info(s"UPDATE: ${o1}")
      try {
        val q =
          table
            .filter(o => o.id == lift(id))
            .update(
              set(_.v, quote(lift(o1.v))),
            )

        ctx.run(q)
        Future.successful(o1)
      } catch {
        case e:Exception => Future.failed(new Exception(s"could not update: ${e}"))
      }
    }
  }

  def del(id:String):Future[String] = {
    log.info(s"DELETE: id=${id}")
    try {
      ctx.run(deleteById(id)) match {
        case 0 => Future.failed(new Exception(s"not found: ${id}"))
        case _ => Future.successful(id)
      }

    } catch {
      case e:Exception => Future.failed(new Exception(s"could not delete: ${e}"))
    }
  }

  def ?(id:String):Future[Odo] = {
    log.info(s"SELECT: id=${id}")
    try {
      ctx.run(table.filter(o => o.id == lift(id))) match {
        case h :: _ => Future.successful(h)
        case Nil => Future.failed(new Exception(s"not found: ${id}"))
      }
    } catch {
      case e:Exception => Future.failed(e)
    }
  }

  def clear():Future[OdoStore] = {
    log.info(s"CLEAR: ")

    val TRUNCATE_TABLE_SQL =
      s"""TRUNCATE TABLE ${tableName};"""

    try {
      ctx.executeAction(TRUNCATE_TABLE_SQL)(ExecutionInfo.unknown, ())
      Future.successful(this)
    } catch {
      case e:Exception => Future.failed(e)
    }
  }

}
