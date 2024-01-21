package io.syspulse.skel.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import java.time._

import scala.util.Try

import io.getquill._
import io.getquill.context.jdbc._
import io.getquill.MysqlJdbcContext
import io.getquill.PostgresJdbcContext
import io.getquill.PostgresJAsyncContext
import io.getquill.MysqlJAsyncContext
//import io.getquill.{Literal, MySQLDialect}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.uri.JdbcURI

abstract class StoreDBCore(dbUri:String,val tableName:String,configuration:Option[Configuration]=None) {
  val log = Logger(s"${this}")

  val props = new java.util.Properties

  val uri = new JdbcURI(dbUri)
    
  protected val (dbType,dbConfigName) = (uri.dbType,uri.dbConfig.getOrElse("posgtres"))

  def getTableName = tableName
  def getDbType = dbType
  def getDbConfigName = dbConfigName

  log.info(s"StoreDB: database=${dbType},config=${dbConfigName},table=${tableName}")

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
abstract class StoreDB[E,P](dbUri:String,tableName:String,configuration:Option[Configuration]=None) 
  extends StoreDBCore(dbUri,tableName,configuration) 
  with Store[E,P] {
  
  val ctx = dbType match {
    case "mysql" => 
      new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
    case "postgres" => 
      // ATTENTION: postgres context does not support UUID as String!
      // Using it as MySQL will work !      
      new PostgresJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
      //new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
    case _ => 
      new MysqlJdbcContext(NamingStrategy(SnakeCase),new HikariDataSource(hikariConfig))
  }

  import ctx._
  
  def create:Try[Long]
  
  // MySQL does not support parameterized SELECT
  val totalSQL = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  //def truncate() = ctx.executeAction(s"TRUNCATE TABLE ${tableName}")
  def truncateSQL = () => quote { infix"""TRUNCATE TABLE ${lift(tableName)}""".as[Long] }
  def truncate():Long = ctx.run(truncateSQL())
  def size:Long = ctx.run(totalSQL())

  // create Store
  create
}

// ========================================================================= StoreDBAsync

abstract class StoreDBAsync[E,P](dbUri:String,tableName:String,configuration:Option[Configuration]=None) 
  extends StoreDBCore(dbUri,tableName,configuration) 
  with StoreAsync[E,P] {

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  
  // for some reason async does not support DataSource    
  val config = ConfigFactory.load().getConfig(dbConfigName)
  log.info(s"DB Config: ${config}")

  val ctx = dbType match {
    case "postgres" =>                   
      // new PostgresAsyncContext(NamingStrategy(SnakeCase),config)    
      new PostgresJAsyncContext(NamingStrategy(SnakeCase),config)      
    case "mysql" => 
      new MysqlJAsyncContext(NamingStrategy(SnakeCase),config)
    case _ =>
      new MysqlJAsyncContext(NamingStrategy(SnakeCase),config)      
  }

  import ctx._
  
  def create:Try[Long]
  
  // Fucking MySQL does not support parameterized SELECT
  val totalSQL = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  //def truncate() = ctx.executeAction(s"TRUNCATE TABLE ${tableName}")
  def truncateSQL = () => quote { infix"""TRUNCATE TABLE ${lift(tableName)}""".as[Long] }
  def truncate():Future[Long] = ctx.run(truncateSQL())

  def size:Future[Long] = ctx.run(totalSQL())

  // create Store
  create
}