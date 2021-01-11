package io.syspulse.skeleton
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

abstract class StoreDB[E](dbName:String) extends Store[E] {
  val log = Logger(s"${this}")

  val prop = new java.util.Properties
  val config = ConfigFactory.load().getConfig(dbName)
  config.entrySet().asScala.foreach(e => prop.setProperty(e.getKey(), config.getString(e.getKey())))
  val hikariConfig = new HikariConfig(prop)
  val ctx = new MysqlJdbcContext(LowerCase,new HikariDataSource(hikariConfig))
  
  import ctx._
  
  def create:Try[Long]
  
  val total = () => quote { infix"""SELECT count(*) FROM ${lift(dbName)}""".as[Long] }

  def size:Long = ctx.run(total())

  // create Store
  create
}