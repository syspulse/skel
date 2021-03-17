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

abstract class StoreDB[E](val dbConfigName:String,val tableName:String) extends Store[E] {
  val log = Logger(s"${this}")

  val prop = new java.util.Properties
  val config = ConfigFactory.load().getConfig(dbConfigName)
  config.entrySet().asScala.foreach(e => prop.setProperty(e.getKey(), config.getString(e.getKey())))
  val hikariConfig = new HikariConfig(prop)
  val ctx = new MysqlJdbcContext(SnakeCase,new HikariDataSource(hikariConfig))

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