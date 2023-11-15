package io.syspulse.skel.otp.store

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._

import io.getquill._
import io.getquill.MysqlJdbcContext
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.{Literal, MySQLDialect}

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.{Configuration}
import io.syspulse.skel.store.{Store,StoreDB}

import io.syspulse.skel.otp.Otp
import io.getquill.context.ExecutionInfo

class OtpStoreDB(configuration:Configuration,dbConfigRef:String) extends StoreDB[Otp,UUID](dbConfigRef,"otp",Some(configuration)) with OtpStore {

  import ctx._

  def create:Try[Long] = {
    ctx.executeAction(
    s"""CREATE TABLE IF NOT EXISTS ${tableName} (
      id VARCHAR(36) PRIMARY KEY, 
      user_id VARCHAR(36), 
      secret VARCHAR(255), 
      name VARCHAR(255), 
      account VARCHAR(255),
      issuer VARCHAR(255),
      period NUMERIC(3),
      digits NUMERIC(3),
      algo VARCHAR(64)
    );
    """
    )(ExecutionInfo.unknown, ())

    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS otp_name ON otp (name);")
    try {
      val r = ctx.executeAction(s"CREATE INDEX otp_name ON ${tableName} (name);")(ExecutionInfo.unknown, ())
      Success(r)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def all:Seq[Otp] = ctx.run(query[Otp])

  def getForUser(userId:UUID):Seq[Otp] = {
    ctx.run(query[Otp].filter(o => o.uid == lift(userId)))
  }
  
  val deleteById = quote { (id:UUID) => 
    query[Otp].filter(o => o.id == id).delete
  } 

  def +(otp:Otp):Try[OtpStoreDB] = { 
    log.info(s"insert: ${otp}")
    try {
      ctx.run(query[Otp].insertValue(lift(otp))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def del(id:UUID):Try[OtpStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  //def -(otp:Otp):Try[OtpStoreDB] = { this.del(otp.id) }

  def ?(id:UUID):Try[Otp] = {
    log.info(s"select: id=${id}")
    ctx.run(query[Otp].filter(o => o.id == lift(id))) match {
      case h :: _ => Success(h)
      case Nil => Failure(new Exception(s"not found: ${id}"))
    }
  }

}