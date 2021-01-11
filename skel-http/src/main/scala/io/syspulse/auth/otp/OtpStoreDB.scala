package io.syspulse.auth.otp

import io.jvm.uuid._

import io.getquill._
import io.getquill.MysqlJdbcContext
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.{Literal, MySQLDialect}

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory

class OtpStoreDB extends OtpStore {
  
  val prop = new java.util.Properties
  val config = ConfigFactory.load().getConfig("db")
  config.entrySet().asScala.foreach(e => prop.setProperty(e.getKey(), config.getString(e.getKey())))
  val hikariConfig = new HikariConfig(prop)
  val ctx = new MysqlJdbcContext(LowerCase,new HikariDataSource(hikariConfig))
  
  import ctx._

  ctx.executeAction("CREATE TABLE IF NOT EXISTS otp (id VARCHAR(36), secret VARCHAR(255), name VARCHAR(255), uri VARCHAR(255), period INT(3) );")
  ctx.executeAction("CREATE INDEX otp_id ON otp (id);")
  
  val total = () => quote { infix"""SELECT count(*) FROM otp""".as[Long] }

  def getAll:Seq[Otp] = ctx.run(query[Otp])
  def size:Long = ctx.run(total())

  val deleteById = quote { (id:UUID) => 
    query[Otp].filter(o => o.id == id).delete
  }

  def +(otp:Otp):OtpStore = { 
    ctx.run(query[Otp].insert(lift(otp))); 
    this
  }

  def -(id:UUID):OtpStore = { 
    ctx.run(deleteById(lift(id)))
    this
  }
  def -(otp:Otp):OtpStore = { this.-(otp.id); this }

  def get(id:UUID):Option[Otp] = {
    ctx.run(query[Otp].filter(o => o.id == lift(id))) match {
      case h :: _ => Some(h)
      case Nil => None
    }
  }

}