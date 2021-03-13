package io.syspulse.skel.otp

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

import io.syspulse.skel.store.{Store,StoreDB}

class OtpStoreDB extends StoreDB[Otp]("db","otp") with OtpStore {

  import ctx._

  def create:Try[Long] = {
    ctx.executeAction("CREATE TABLE IF NOT EXISTS otp (id VARCHAR(36) PRIMARY KEY, secret VARCHAR(255), name VARCHAR(255), uri VARCHAR(255), period INT(3) );")
    // why do we still use MySQL which does not even support INDEX IF NOT EXISTS ?...
    //val r = ctx.executeAction("CREATE INDEX IF NOT EXISTS otp_name ON otp (name);")
    try {
      val r = ctx.executeAction("CREATE INDEX otp_name ON otp (name);")
      Success(r)
    } catch {
      case e:Exception => { 
        // short name without full stack (change to check for duplicate index)
        log.warn(s"failed to create index: ${e.getMessage()}"); Success(0) 
      }
    }
  }
  
  def getAll:Seq[Otp] = ctx.run(query[Otp])
  
  val deleteById = quote { (id:UUID) => 
    query[Otp].filter(o => o.id == id).delete
  } 

  def +(otp:Otp):Try[OtpStoreDB] = { 
    log.info(s"insert: ${otp}")
    try {
      ctx.run(query[Otp].insert(lift(otp))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[OtpStoreDB] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(otp:Otp):Try[OtpStoreDB] = { this.-(otp.id) }

  def get(id:UUID):Option[Otp] = {
    log.info(s"select: id=${id}")
    ctx.run(query[Otp].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

}