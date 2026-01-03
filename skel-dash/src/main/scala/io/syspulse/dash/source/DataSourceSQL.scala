package io.syspulse.dash.source

import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger
import java.util.concurrent.Executors

import spray.json._
import spray.json.JsObject
import spray.json.JsString

import io.syspulse.dash.server.DashData
import io.syspulse.skel.uri.JdbcURI
import io.syspulse.dash.server.DashDataReq
import io.syspulse.dash.source.DataSource

import java.sql.{Connection, DriverManager, ResultSet, ResultSetMetaData}
import java.util.Properties
import scala.concurrent.blocking
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}

import com.github.jasync.sql.db.{Connection => AsyncConnection, QueryResult, ResultSet => AsyncResultSet, RowData}
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.pool.ConnectionPool
import scala.jdk.CollectionConverters._
import scala.compat.java8.FutureConverters._

object DataSourceSQL {
  val DEFAULT_MAX_POOL_SIZE = 4
  val DEFAULT_MIN_IDLE = 1
  val DEFAULT_CONNECTION_TIMEOUT = 30000L

  /**
   * Escape a CSV value by wrapping in quotes if it contains comma, quote, or newline
   */
  def escapeCsvValue(value: Any): String = {
    val str = if (value == null) "" else value.toString
    if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
      "\"" + str.replace("\"", "\"\"") + "\""
    } else {
      str
    }
  }

  /**
   * Format data as CSV given column names and row data
   * @param columnNames Sequence of column names
   * @param rows Sequence of rows, where each row is a sequence of values
   * @return Tuple of (JsString with CSV content, row count, format string)
   */
  def formatAsCSV(columnNames: Seq[String], rows: Seq[Seq[Any]]): (JsValue, Int, String) = {
    if (rows.isEmpty) {
      return (JsString(""), 0, "csv")
    }

    val sb = new StringBuilder

    // Header row
    sb.append(columnNames.mkString(",")).append("\n")

    // Data rows
    rows.foreach { row =>
      val values = row.map(escapeCsvValue)
      sb.append(values.mkString(",")).append("\n")
    }

    (JsString(sb.toString()), rows.length, "csv")
  }

  /**
   * Convert a value to JsValue based on its type
   */
  def toJsValue(value: Any): JsValue = {
    if (value == null) {
      JsNull
    } else {
      value match {
        case v: java.lang.Integer => JsNumber(v.intValue())
        case v: java.lang.Long => JsNumber(v.longValue())
        case v: java.lang.Double => JsNumber(v.doubleValue())
        case v: java.lang.Float => JsNumber(v.doubleValue())
        case v: java.math.BigDecimal => JsNumber(v)
        case v: Boolean => JsBoolean(v)
        case v => JsString(v.toString)
      }
    }
  }

  /**
   * Format data as JSON given column names and row data
   * @param columnNames Sequence of column names
   * @param rows Sequence of rows, where each row is a sequence of values
   * @return Tuple of (JsArray with JSON content, row count, format string)
   */
  def formatAsJSON(columnNames: Seq[String], rows: Seq[Seq[Any]]): (JsValue, Int, String) = {
    if (rows.isEmpty) {
      return (JsArray.empty, 0, "json")
    }

    val jsonRows = rows.map { row =>
      val fields = columnNames.zip(row).map { case (colName, value) =>
        (colName, toJsValue(value))
      }.toMap
      JsObject(fields)
    }

    (JsArray(jsonRows.toVector), rows.length, "json")
  }
}

class DataSourceSQL(uri0:String) extends DataSource {
  private val log = Logger(this.getClass)

  val dbUri = JdbcURI(uri0)

  // Use JdbcURI's getJdbcUrl method (includes query parameters like ?TimeZone=UTC)
  val jdbcUrl = dbUri.getJdbcUrl(async = false)

  // Get timezone from URI, default to UTC
  private val targetTimezone = dbUri.timezone.getOrElse("UTC")

  // Configure HikariCP with connection initialization SQL
  private val hikariConfig = {
    val config = new HikariConfig()
    config.setJdbcUrl(jdbcUrl)
    if (dbUri.user.isDefined) config.setUsername(dbUri.user.get)
    if (dbUri.pass.isDefined) config.setPassword(dbUri.pass.get)

    // Set datasource properties to configure timezone during connection establishment
    config.addDataSourceProperty("TimeZone", targetTimezone)

    // Connection pool settings
    config.setMaximumPoolSize(dbUri.opts.get("MaxPoolSize").map(_.toInt).getOrElse(DataSourceSQL.DEFAULT_MAX_POOL_SIZE))
    config.setMinimumIdle(dbUri.opts.get("MinIdle").map(_.toInt).getOrElse(DataSourceSQL.DEFAULT_MIN_IDLE))
    config.setConnectionTimeout(dbUri.opts.get("ConnectionTimeout").map(_.toLong).getOrElse(DataSourceSQL.DEFAULT_CONNECTION_TIMEOUT))

    config
  }

  // Temporarily set timezone for HikariCP initialization to avoid PostgreSQL timezone errors
  val tz = System.getProperty("user.timezone")
  System.setProperty("user.timezone", targetTimezone)
  java.util.TimeZone.setDefault(null)
  private val dataSource = new HikariDataSource(hikariConfig)
  System.setProperty("user.timezone", tz)

  // Configure async connection pool (jasync-sql)
  // Uses JdbcURI.getJdbcUrl(async=true) which returns jasync-sql format
  private val asyncConnectionPool = {
    val connectionStr = dbUri.getJdbcUrl(async = true)
    
    // Create appropriate connection pool based on database type
    dbUri.dbType match {
      case "postgres" | "postgresql" =>
        PostgreSQLConnectionBuilder.createConnectionPool(connectionStr)
      case "mysql" =>
        MySQLConnectionBuilder.createConnectionPool(connectionStr)
      case other =>
        throw new IllegalArgumentException(s"Unsupported database type for async execution: ${other}. Supported types: postgres, mysql")
    }
  }

  // Helper method to get connection from pool
  private def getConnection(): Connection = {
    dataSource.getConnection()
  }

  log.info(s"DataSourceSQL: '${uri0}' (${jdbcUrl})")

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

  def src: String = "sql"  

  def ask(req:DashDataReq, tid:Option[String] = None): Future[DashData] = {
    if(req.src != this.src) {
      return Future.failed(new Exception(s"unsupported datasource: '${req.src}'"))
    }

    val ts0 = System.currentTimeMillis()

    // Extract SQL query and format from req.query
    val (sqlQuery, formatFromQuery) = req.query match {
      case Some(JsString(q)) => (q, None)
      case Some(JsObject(fields)) =>
        val query = fields.get("query") match {
          case Some(JsString(q)) => q
          case Some(JsObject(_)) =>
            // If "query" is an object, try "sql" inside it
            fields.get("sql") match {
              case Some(JsString(q)) => q
              case _ => return Future.failed(new Exception("SQL query not found in request.query"))
            }
          case None =>
            // If "query" doesn't exist, try "sql" at top level
            fields.get("sql") match {
              case Some(JsString(q)) => q
              case _ => return Future.failed(new Exception("SQL query not found in request.query"))
            }
          case _ => return Future.failed(new Exception("SQL query not found in request.query"))
        }
        // Extract format from query object if present
        val format = fields.get("format").collect { case JsString(f) => f }
        (query, format)
      case _ => return Future.failed(new Exception("SQL query not provided in request.query"))
    }

    // Determine output format: prefer formatFromQuery, then req.fmt, then default to json
    val outputFormat = formatFromQuery.orElse(req.fmt).getOrElse("json")

    // Determine execution type (sync/async) from req.typ
    val executionType = req.typ.getOrElse("sync")

    log.info(s"Executing SQL query (${executionType}): '${sqlQuery}' -> ${jdbcUrl} (format: ${outputFormat}, type: ${executionType})")

    // Execute query based on type
    executionType match {
      case "async" => executeAsync(req, sqlQuery, outputFormat, ts0)
      case _ => executeSync(req, sqlQuery, outputFormat, ts0)
    }
  }

  private def executeSync(req: DashDataReq, sqlQuery: String, outputFormat: String, ts0: Long): Future[DashData] = {
    Future {
      blocking {
        val connection = getConnection()
        try {
          val statement = connection.createStatement()
          try {
            val resultSet = statement.executeQuery(sqlQuery)
            try {
              val (resultData, rowCount, format) = outputFormat match {
                case "csv" => formatResultSetAsCSV(resultSet)
                case _ => formatResultSetAsJSON(resultSet)
              }

              val json = JsObject(
                "query" -> JsString(sqlQuery),
                "result" -> resultData,
                "rows" -> JsNumber(rowCount)
              )

              DashData(
                id = req.id,
                src = src,
                fmt = format,
                data = json,
                ts0 = ts0,
                ts = System.currentTimeMillis()
              )
            } finally {
              resultSet.close()
            }
          } finally {
            statement.close()
          }
        } finally {
          connection.close()
        }
      }
    }
  }

  private def executeAsync(req: DashDataReq, sqlQuery: String, outputFormat: String, ts0: Long): Future[DashData] = {
    asyncConnectionPool.sendQuery(sqlQuery).toScala.map { queryResult =>
      val rows = queryResult.getRows.asScala.toSeq

      val (resultData, rowCount, format) = outputFormat match {
        case "csv" => formatAsyncResultAsCSV(queryResult)
        case _ => formatAsyncResultAsJSON(queryResult)
      }

      val json = JsObject(
        "query" -> JsString(sqlQuery),
        "result" -> resultData,
        "rows" -> JsNumber(rowCount)
      )

      DashData(
        id = req.id,
        src = src,
        fmt = format,
        data = json,
        ts0 = ts0,
        ts = System.currentTimeMillis()
      )
    }
  }

  private def formatResultSetAsCSV(rs: ResultSet): (JsValue, Int, String) = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount

    // Get column names
    val columnNames = (1 to columnCount).map(i => metaData.getColumnName(i))

    // Extract all rows
    val rows = scala.collection.mutable.ListBuffer[Seq[Any]]()
    while (rs.next()) {
      val row = (1 to columnCount).map(i => rs.getObject(i))
      rows += row
    }

    DataSourceSQL.formatAsCSV(columnNames, rows.toSeq)
  }

  private def formatResultSetAsJSON(rs: ResultSet): (JsValue, Int, String) = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount

    // Get column names
    val columnNames = (1 to columnCount).map(i => metaData.getColumnName(i))

    // Extract all rows
    val rows = scala.collection.mutable.ListBuffer[Seq[Any]]()
    while (rs.next()) {
      val row = (1 to columnCount).map(i => rs.getObject(i))
      rows += row
    }

    DataSourceSQL.formatAsJSON(columnNames, rows.toSeq)
  }

  private def formatAsyncResultAsCSV(queryResult: QueryResult): (JsValue, Int, String) = {
    val rows = queryResult.getRows.asScala.toSeq
    if (rows.isEmpty) {
      return (JsString(""), 0, "csv")
    }

    // Get column count from first row (RowData extends List)
    val columnCount = rows.head.size()
    // Generate column names as col_0, col_1, etc. since jasync doesn't expose column metadata
    val columnNames = (0 until columnCount).map(i => s"col_$i")

    // Convert RowData to Seq[Seq[Any]]
    val rowsData = rows.map { row =>
      (0 until columnCount).map(idx => row.get(idx))
    }

    DataSourceSQL.formatAsCSV(columnNames, rowsData)
  }

  private def formatAsyncResultAsJSON(queryResult: QueryResult): (JsValue, Int, String) = {
    val rows = queryResult.getRows.asScala.toSeq
    if (rows.isEmpty) {
      return (JsArray.empty, 0, "json")
    }

    // Get column count from first row (RowData extends List)
    val columnCount = rows.head.size()
    // Generate column names as col_0, col_1, etc. since jasync doesn't expose column metadata
    val columnNames = (0 until columnCount).map(i => s"col_$i")

    // Convert RowData to Seq[Seq[Any]]
    val rowsData = rows.map { row =>
      (0 until columnCount).map(idx => row.get(idx))
    }

    DataSourceSQL.formatAsJSON(columnNames, rowsData)
  }
}
