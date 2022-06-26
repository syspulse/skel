package io.syspulse.skel.store

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._
import java.time._

import scala.util.Try

import io.getquill._
import io.getquill.MysqlJdbcContext
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.{Literal, MySQLDialect}

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.Configuration

abstract class StoreDB[E,P](val dbConfigName:String,val tableName:String,configuration:Option[Configuration]=None) extends Store[E,P] {
  val log = Logger(s"${this}")

  val props = new java.util.Properties
  if( ! configuration.isDefined) {
    val config = ConfigFactory.load().getConfig(dbConfigName)
    config.entrySet().asScala.foreach(
      e => props.setProperty(e.getKey(), config.getString(e.getKey()))
    )
  } else {
    val prefix = if(dbConfigName.isBlank) "" else dbConfigName + "."

    Set("dataSourceClassName","dataSource.url","dataSource.user","dataSource.password",
        "connectionTimeout","idleTimeout","minimumIdle","maximumPoolSize","poolName","maxLifetime")
    .map(p => 
      // Null is needed to detect non-set field
      (p -> configuration.get.getString(s"${prefix}${p}").getOrElse(null))
    )
    .foreach{
      case(k,v) => if(v!=null) props.setProperty(k,v)
    }
  }
  log.info(s"HikariProperties: ${props}")
  val hikariConfig = new HikariConfig(props)
  //val ctx = new MysqlJdbcContext(NamingStrategy(SnakeCase, UpperCase),new HikariDataSource(hikariConfig))
  val ctx = new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
  
  import ctx._

  // Always store UTC timestamp
  def utc(z:ZonedDateTime) = z.withZoneSameInstant( ZoneId.of("UTC"))
  def local(d:LocalDateTime) = d.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.systemDefault())

  implicit val encodeZonedDateTime = MappedEncoding[ZonedDateTime, LocalDateTime](z => utc(z).toLocalDateTime)
  implicit val decodeZonedDateTime = MappedEncoding[LocalDateTime, ZonedDateTime]( d => local(d))
  
  import ctx._
  
  def create:Try[Long]
  
  // Fucking MySQL does not support parameterized SELECT
  val total = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  def truncate() = ctx.executeAction(s"TRUNCATE TABLE ${tableName}")

  def size:Long = ctx.run(total())

  // create Store
  create
}