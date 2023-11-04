package io.syspulse.skel.store

import scala.util.Try
import scala.util.{Success,Failure}

import io.jvm.uuid._
import java.time._

import scala.util.Try

import io.getquill._
import io.getquill.MysqlJdbcContext
import io.getquill.PostgresJdbcContext
import io.getquill.PostgresAsyncContext

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
//import io.getquill.{Literal, MySQLDialect}

import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.config.Configuration
import scala.concurrent.Future

abstract class StoreDBCore[E,P](val dbConfigName:String,val tableName:String,configuration:Option[Configuration]=None) {
  val log = Logger(s"${this}")

  val props = new java.util.Properties

  if( ! configuration.isDefined) {
    val config = ConfigFactory.load().getConfig(dbConfigName)
    config.entrySet().asScala.foreach(
      e => props.setProperty(e.getKey(), config.getString(e.getKey()))
    )
  } else {
    // Java11: use isBlank
    val prefix = if(dbConfigName.trim.isEmpty) "" else dbConfigName + "."

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
  
  // val ctx = dbConfigName.split("://").toList match {
  //   case "mysql" :: _ => 
  //     new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
  //   case "postgres" :: _ => 
  //     // postgres context does not support UUID as String!
  //     //new PostgresJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
  //     new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
  //   // case "async" :: _ | "postgres_async" :: _ => 
  //   //   // for some reason async does not support DataSource
  //   //   val config = ConfigFactory.load().getConfig(dbConfigName)
  //   //   new PostgresAsyncContext(NamingStrategy(SnakeCase),config)
  //   case _ => 
  //     new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
  // }

  // import ctx._

  // Always store UTC timestamp
  def utc(z:ZonedDateTime) = z.withZoneSameInstant( ZoneId.of("UTC"))
  def local(d:LocalDateTime) = d.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.systemDefault())

  implicit val encodeZonedDateTime = MappedEncoding[ZonedDateTime, LocalDateTime](z => utc(z).toLocalDateTime)
  implicit val decodeZonedDateTime = MappedEncoding[LocalDateTime, ZonedDateTime]( d => local(d))
    
}

// ========================================================================= StoreDB
abstract class StoreDB[E,P](dbConfigName:String,tableName:String,configuration:Option[Configuration]=None) 
  extends StoreDBCore[E,P](dbConfigName,tableName,configuration) 
  with Store[E,P] {
  
  val ctx = dbConfigName.split("://").toList match {
    case "mysql" :: _ => 
      new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
    case "postgres" :: _ => 
      // postgres context does not support UUID as String!
      //new PostgresJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
      new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
    case _ => 
      new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
  }

  import ctx._
  
  def create:Try[Long]
  
  // MySQL does not support parameterized SELECT
  val total = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  def truncate() = ctx.executeAction(s"TRUNCATE TABLE ${tableName}")

  def size:Long = ctx.run(total())

  // create Store
  create
}

// ========================================================================= StoreDBAsync

abstract class StoreDBAsync[E,P](dbConfigName:String,tableName:String,configuration:Option[Configuration]=None) 
  extends StoreDBCore[E,P](dbConfigName,tableName,configuration) 
  with StoreAsync[E,P] {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  val ctx = dbConfigName.split("://").toList match {
    case "async" :: _ | "postgres" :: _ => 
      // for some reason async does not support DataSource
      val config = ConfigFactory.load().getConfig(dbConfigName)
      new PostgresAsyncContext(NamingStrategy(SnakeCase),config)    
  }

  import ctx._
  
  def create:Try[Long]
  
  // Fucking MySQL does not support parameterized SELECT
  val total = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  def truncate() = ctx.executeAction(s"TRUNCATE TABLE ${tableName}")

  def size:Future[Long] = ctx.run(total())

  // create Store
  create
}