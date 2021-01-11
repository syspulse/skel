package io.syspulse.auth.otp

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

class OtpStoreDB extends OtpStore {
  val log = Logger(s"${this}")

  val prop = new java.util.Properties
  val config = ConfigFactory.load().getConfig("db")
  config.entrySet().asScala.foreach(e => prop.setProperty(e.getKey(), config.getString(e.getKey())))
  val hikariConfig = new HikariConfig(prop)
  val ctx = new MysqlJdbcContext(LowerCase,new HikariDataSource(hikariConfig))
  
  import ctx._

  ctx.executeAction("CREATE TABLE IF NOT EXISTS otp (id VARCHAR(36) PRIMARY KEY, secret VARCHAR(255), name VARCHAR(255), uri VARCHAR(255), period INT(3) );")
  ctx.executeAction("CREATE INDEX IF NOT EXISTS otp_name ON otp (name);")
  
  val total = () => quote { infix"""SELECT count(*) FROM otp""".as[Long] }

  def getAll:Seq[Otp] = ctx.run(query[Otp])
  def size:Long = ctx.run(total())

  val deleteById = quote { (id:UUID) => 
    query[Otp].filter(o => o.id == id).delete
  }

  def +(otp:Otp):Try[OtpStore] = { 
    log.info(s"insert: ${otp}")
    try {
      ctx.run(query[Otp].insert(lift(otp))); 
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not insert: ${e}"))
    }
  }

  def -(id:UUID):Try[OtpStore] = { 
    log.info(s"delete: id=${id}")
    try {
      ctx.run(deleteById(lift(id)))
      Success(this)
    } catch {
      case e:Exception => Failure(new Exception(s"could not delete: ${e}"))
    } 
  }
  def -(otp:Otp):Try[OtpStore] = { this.-(otp.id) }

  def get(id:UUID):Option[Otp] = {
    log.info(s"select: id=${id}")
    ctx.run(query[Otp].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

}