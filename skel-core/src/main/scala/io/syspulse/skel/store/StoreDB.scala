package io.syspulse.skel.store

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._

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
  val ctx = new MysqlJdbcContext(LowerCase,new HikariDataSource(hikariConfig))
  
  import ctx._
  
  def create:Try[Long]
  
  // Fucking MySQL does not support parameterized SELECT
  val total = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  def truncate() = ctx.executeAction(s"TRUNCATE TABLE ${tableName}")

  def size:Long = ctx.run(total())

  // create Store
  create
}