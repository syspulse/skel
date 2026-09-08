package io.syspulse.skel.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._
import java.time._

import scala.util.Try

import io.getquill._
import io.getquill.context._
import io.getquill.context.jdbc._
import io.getquill.MysqlJdbcContext
import io.getquill.PostgresJdbcContext
import io.getquill.PostgresJAsyncContext
import io.getquill.MysqlJAsyncContext


//import io.getquill.{Literal, MySQLDialect}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import io.syspulse.skel.config.Configuration
import io.syspulse.skel.uri.JdbcURI
import io.syspulse.skel.util.Util

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

/** Search index kinds for DB stores (Postgres: fts=tsvector, tgram=pg_trgm). */
object StoreSearch {
  val Fts = "fts"
  val Tgram = "tgram"

  private val Known = Set(Fts, Tgram)

  def parse(value: String): Set[String] = {
    val cleaned = value.trim.stripPrefix("\"").stripSuffix("\"").trim
    if (cleaned.isEmpty) Set.empty
    else cleaned.split("[,+]").map(_.trim.toLowerCase).filter(Known.contains).toSet
  }

  /** URI `?search=` overrides constructor; default is fts-only when unset. */
  def resolve(uri: JdbcURI, constructor: Option[Set[String]]): Set[String] =
    uri.opts.get("search") match {
      case Some(v) => parse(v)
      case None    => constructor.getOrElse(Set(Fts))
    }

  def hasFts(indexes: Set[String]): Boolean = indexes.contains(Fts)
  def hasTgram(indexes: Set[String]): Boolean = indexes.contains(Tgram)
}

/** Shared free-text search helpers (Postgres tsvector + pg_trgm + query tokenization). */
object StoreFts {
  val SEARCH_MIN_LEN = 3

  private val NonAlnum = "[^a-zA-Z0-9]+"
  private val CamelSplit = "([a-z])([A-Z])"

  /** "DetectorWallet" -> "Detector Wallet" so FTS can match "wallet" inside compound text. */
  def splitCamelCase(text: String): String =
    text.replaceAll(CamelSplit, "$1 $2")

  def normalizeSearchQuery(query: String): String = {
    val q = query.trim
    if (q.length >= 2 && ((q.head == '\'' && q.last == '\'') || (q.head == '"' && q.last == '"'))) q.substring(1, q.length - 1).trim
    else q
  }

  def tokenizeSearchField(text: String): Seq[String] =
    splitCamelCase(text).toLowerCase.replaceAll(NonAlnum, " ").split("\\s+").filter(_.nonEmpty)

  def postgresSearchTerms(query: String): Seq[String] =
    tokenizeSearchField(normalizeSearchQuery(query))

  /** Postgres prefix tsquery: "yuk" -> "yuk:*" matches token prefix "yuki". */
  def postgresPrefixTsQuery(query: String): Option[String] = {
    val terms = postgresSearchTerms(query)
    if (terms.isEmpty) None else Some(terms.map(t => s"$t:*").mkString(" & "))
  }

  /** SQL expression: split camelCase and non-alphanumerics for one column. */
  def pgTokenizeColumn(col: String): String =
    s"regexp_replace(regexp_replace(coalesce($col, ''), '([a-z])([A-Z])', '\\1 \\2', 'g'), '[^a-zA-Z0-9]+', ' ', 'g')"

  /** Combined Postgres tsv source expression for multiple searchable columns. */
  def pgTsvExpr(fields: Seq[String]): String =
    fields.map(pgTokenizeColumn).mkString(" || ' ' || ")

  /** Substring match on text columns (uses pg_trgm GIN indexes when created). */
  def pgTrgmWhere(fields: Seq[String], query: String, sqlLit: String => String): String = {
    val pattern = sqlLit(query.toLowerCase)
    fields.map(f => s"lower(coalesce($f, '')) LIKE '%$pattern%'").mkString(" OR ")
  }

  /** Postgres WHERE for enabled search indexes (fts and/or tgram combined with OR). */
  def postgresSearchWhere(
    searchIndexes: Set[String],
    tsvCol: Option[String],
    trgmFields: Seq[String],
    query: String,
    sqlLit: String => String,
  ): Option[String] = {
    val ftsPart =
      if (StoreSearch.hasFts(searchIndexes) && tsvCol.isDefined)
        postgresPrefixTsQuery(query).map(tsq => s"${tsvCol.get} @@ to_tsquery('simple', '${sqlLit(tsq)}')")
      else None
    val tgramPart =
      if (StoreSearch.hasTgram(searchIndexes) && trgmFields.nonEmpty)
        Some(pgTrgmWhere(trgmFields, query, sqlLit))
      else None
    val parts = Seq(ftsPart, tgramPart).flatten
    if (parts.isEmpty) None else Some(parts.map(p => s"($p)").mkString(" OR "))
  }
}

abstract class StoreDBCore(dbUri:String,val tableName:String,configuration:Option[Configuration]=None,searchIndexesOpt:Option[Set[String]]=None) {
  private val log = Logger(s"${this}")

  val props = new java.util.Properties
  val uri = new JdbcURI(dbUri)

  log.info(s"dbUri=${dbUri},uri=${uri},tableName=${tableName},configuration=${configuration}")

  protected val (dbType,dbConfigName) = (uri.dbType,uri.dbConfig.getOrElse("postgres"))
  protected val dbTimezone = uri.timezone.getOrElse("UTC")
  protected val searchIndexes: Set[String] = StoreSearch.resolve(uri, searchIndexesOpt)

  def getTableName = tableName
  def getDbType = dbType
  def getDbConfigName = dbConfigName

  log.info(s"StoreDB: database='${dbType}',config='${dbConfigName}',table='${tableName}',searchIndexes='${searchIndexes.mkString(",")}'")

  if( ! configuration.isDefined) {
    val config = ConfigFactory.load().getConfig(dbConfigName).resolve()
    config.entrySet().asScala.foreach(
      e => props.setProperty(e.getKey(), config.getString(e.getKey()))
    )
  } else {
    // Java11: use isBlank
    val prefix = if(dbConfigName.trim.isEmpty) "" else dbConfigName + "."

    Set(
      "dataSourceClassName",
      "dataSource.url",
      "dataSource.user",
      "dataSource.password",
      "connectionTimeout",
      "idleTimeout",
      "minimumIdle",
      "maximumPoolSize",
      "poolName",
      "maxLifetime",

    )
    .map(p =>
      // Null is needed to detect non-set field
      (p -> configuration.get.getString(s"${prefix}${p}").getOrElse(null))
    )
    .foreach{
      case(k,v) => if(v!=null) props.setProperty(k,v)
    }
  }

  // ATTENTION: do not log password !
  log.info(s"Hikari Properties: ${props.asScala.map{case (k,v) => if(k == "dataSource.password") s"${k}=${Util.trunc(v,6)}" else s"${k}=${v}"}.mkString(",")}")

  val hikariConfig = new HikariConfig(props)

  // Always store UTC timestamp
  def utc(z:ZonedDateTime) = z.withZoneSameInstant( ZoneId.of("UTC"))
  def local(d:LocalDateTime) = d.atZone(ZoneId.of("UTC")).withZoneSameInstant( ZoneId.systemDefault())

  implicit val encodeZonedDateTime = MappedEncoding[ZonedDateTime, LocalDateTime](z => utc(z).toLocalDateTime)
  implicit val decodeZonedDateTime = MappedEncoding[LocalDateTime, ZonedDateTime]( d => local(d))

}

// ========================================================================= StoreDB
abstract class StoreDB[E,P](dbUri:String,tableName:String,configuration:Option[Configuration]=None,searchIndexesOpt:Option[Set[String]]=None)
  extends StoreDBCore(dbUri,tableName,configuration,searchIndexesOpt)
  with Store[E,P] {

  val tz = System.getProperty("user.timezone")
  System.setProperty("user.timezone", dbTimezone)
  java.util.TimeZone.setDefault(null)

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

  System.setProperty("user.timezone", tz)

  import ctx._

  def create:Try[Long]

  // MySQL does not support parameterized SELECT
  val totalSQL = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  def truncateSQL = () => quote { infix"""TRUNCATE TABLE ${lift(tableName)}""".as[Long] }
  def truncate():Long = ctx.run(truncateSQL())
  def size:Future[Long] = Future.successful(ctx.run(totalSQL()))

  implicit val vectorStringDecoder: Decoder[Vector[String]] =
    decoder((row: ResultRow) => (index: Index) => {
      val str = row.getString(index)
      if (str == null || str.isEmpty) Vector.empty[String] else str.split(",").toVector
    })

  implicit val vectorStringEncoding: MappedEncoding[Vector[String], String] =
    MappedEncoding[Vector[String], String](_.mkString(","))

  // create Store
  create
}

// ========================================================================= StoreDBAsync

abstract class StoreDBAsync[E,P](dbUri:String,tableName:String,configuration:Option[Configuration]=None,searchIndexesOpt:Option[Set[String]]=None)
  extends StoreDBCore(dbUri,tableName,configuration,searchIndexesOpt)
  with Store[E,P] {

  private val log = Logger(s"${this}")

  implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
  val timeout = 15000L

  // for some reason async does not support DataSource
  // val config = ConfigFactory.load().getConfig(dbConfigName).resolve()  
  val config = if( ! configuration.isDefined) {
    ConfigFactory.load().getConfig(dbConfigName).resolve()
    
  } else {
    val prefix = if (dbConfigName.trim.isEmpty) "" else dbConfigName + "."

    Set(
      "dataSourceClassName",
      "url",
      "database",
      "username",
      "password",
      "numThreads",
      "dataSource.connectionProperties",
    ).foldLeft(ConfigFactory.empty()) { (c, key) =>
      configuration.get.getString(s"${prefix}${key}") match {
        case Some(v) => c.withValue(key, ConfigValueFactory.fromAnyRef(v))
        case None    => c
      }
    }
  }

  log.info(s"DB Config: ${config}")

  val ctx = dbType match {
    case "postgres" =>
      new PostgresJAsyncContext(NamingStrategy(SnakeCase),config)
    case "mysql" =>
      new MysqlJAsyncContext(NamingStrategy(SnakeCase),config)
    case _ =>
      new MysqlJAsyncContext(NamingStrategy(SnakeCase),config)
  }

  import ctx._

  def create:Try[Long]

  val totalSQL = () => quote { infix"""SELECT count(*) FROM ${lift(tableName)}""".as[Long] }
  def truncateSQL = () => quote { infix"""TRUNCATE TABLE ${lift(tableName)}""".as[Long] }
  def truncate():Future[Long] = ctx.run(truncateSQL())

  def size:Future[Long] = ctx.run(totalSQL())

  /** Recreate generated tsv column (idempotent) after tokenization expression changes. */
  protected def migrateTsvPostgres(colTsv: String, indexFts: String, tsvExpr: String): Unit =
    migrateTsvPostgres(tableName, colTsv, indexFts, tsvExpr)

  protected def migrateTsvPostgres(table: String, colTsv: String, indexFts: String, tsvExpr: String): Unit = {
    val dropIdx = s"DROP INDEX IF EXISTS $indexFts"
    val dropCol = s"ALTER TABLE $table DROP COLUMN IF EXISTS $colTsv"
    val addCol =
      s"""ALTER TABLE $table ADD COLUMN $colTsv tsvector GENERATED ALWAYS AS (
         |  to_tsvector('simple', $tsvExpr)
         |) STORED""".stripMargin
    val createFtsIndexSql = s"CREATE INDEX IF NOT EXISTS $indexFts ON $table USING GIN ($colTsv);"
    try {
      Seq(dropIdx, dropCol, addCol, createFtsIndexSql).foreach { sql =>
        val f = ctx.executeAction(sql)(ExecutionInfo.unknown, ())
        Await.result(f, FiniteDuration(timeout, TimeUnit.MILLISECONDS))
      }
      log.info(s"table: $table: migrated $colTsv")
    } catch {
      case e: Exception =>
        log.warn(s"table: $table: $colTsv migration skipped: ${e.getMessage()}")
    }
  }

  /** Create pg_trgm GIN indexes on text columns (substring / ILIKE search).
    * Requires `pg_trgm` extension (install via db/postgres/db-create.sql as superuser). */
  protected def createTrgramIndexes(indexPrefix: String, fields: Seq[String]): Unit =
    createTrgramIndexes(tableName, indexPrefix, fields)

  protected def createTrgramIndexes(table: String, indexPrefix: String, fields: Seq[String]): Unit = {
    try {
      fields.foreach { field =>
        val idx = s"${indexPrefix}_${field}_trgm"
        val sql = s"CREATE INDEX IF NOT EXISTS $idx ON $table USING GIN ($field gin_trgm_ops)"
        val f = ctx.executeAction(sql)(ExecutionInfo.unknown, ())
        Await.result(f, FiniteDuration(timeout, TimeUnit.MILLISECONDS))
        log.info(s"index: $idx: ok")
      }
    } catch {
      case e: Exception =>
        log.warn(s"table: $table: trgram indexes skipped: ${e.getMessage()}")
    }
  }

  protected def setupPostgresSearchIndexes(
    colTsv: String,
    indexFts: String,
    tsvExpr: String,
    indexTgramPrefix: String,
    tgramFields: Seq[String],
  ): Unit =
    setupPostgresSearchIndexes(tableName, colTsv, indexFts, tsvExpr, indexTgramPrefix, tgramFields)

  protected def setupPostgresSearchIndexes(
    table: String,
    colTsv: String,
    indexFts: String,
    tsvExpr: String,
    indexTgramPrefix: String,
    tgramFields: Seq[String],
  ): Unit = {
    if (StoreSearch.hasFts(searchIndexes))
      migrateTsvPostgres(table, colTsv, indexFts, tsvExpr)
    if (StoreSearch.hasTgram(searchIndexes))
      createTrgramIndexes(table, indexTgramPrefix, tgramFields)
  }

  protected def postgresTsvColumnDef(colTsv: String, tsvExpr: String): String =
    if (StoreSearch.hasFts(searchIndexes))
      s"""$colTsv tsvector GENERATED ALWAYS AS (
         |  to_tsvector('simple', $tsvExpr)
         |) STORED,""".stripMargin
    else
      ""

  // create Store
  create
}
