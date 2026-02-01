package io.syspulse.skel.dash.source

import scala.concurrent.{Future, ExecutionContext}
import com.typesafe.scalalogging.Logger
import java.util.concurrent.Executors
import scala.jdk.CollectionConverters._
import scala.compat.java8.FutureConverters._

import spray.json._
import spray.json.JsObject
import spray.json.JsString

import io.syspulse.skel.uri.JdbcURI
import io.syspulse.skel.dash.server.DashData
import io.syspulse.skel.dash.server.DashDataReq
import io.syspulse.skel.dash.source.DataSource

import java.sql.{Connection, DriverManager, ResultSet, ResultSetMetaData}
import java.util.Properties
import scala.concurrent.blocking
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import java.time.{ZonedDateTime, ZoneOffset, Instant}
import java.time.format.DateTimeFormatter

import com.github.jasync.sql.db.{Connection => AsyncConnection, QueryResult, ResultSet => AsyncResultSet, RowData}
import com.github.jasync.sql.db.postgresql.PostgreSQLConnectionBuilder
import com.github.jasync.sql.db.mysql.MySQLConnectionBuilder
import com.github.jasync.sql.db.pool.ConnectionPool

import io.syspulse.skel.db.guard.QueryGuard
import io.syspulse.skel.db.guard.QueryGuardAllow
//import io.r2dbc.spi.{Connection, Result, Row}

import io.syspulse.skel.util.Util
import scala.util.Failure
import scala.util.Success

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

  /**
   * Format data as JSON2 given column names and row data
   * Returns an object with "fields" (array of column names) and "data" (array of row arrays)
   * @param columnNames Sequence of column names
   * @param rows Sequence of rows, where each row is a sequence of values
   * @return Tuple of (JsObject with fields and data, row count, format string)
   */
  def formatAsJSON2(columnNames: Seq[String], rows: Seq[Seq[Any]]): (JsValue, Int, String) = {
    val fieldsArray = JsArray(columnNames.map(JsString(_)).toVector)
    val dataArray = if (rows.isEmpty) {
      JsArray.empty
    } else {
      JsArray(rows.map { row =>
        JsArray(row.map(toJsValue).toVector)
      }.toVector)
    }

    val result = JsObject(
      "fields" -> fieldsArray,
      "data" -> dataArray
    )

    (result, rows.length, "json2")
  }

  /**
   * Format data as Dune format given column names, column types, row data, and execution metadata
   * Returns an object matching Dune API response format
   * @param columnNames Sequence of column names
   * @param columnTypes Sequence of column type names
   * @param rows Sequence of rows, where each row is a sequence of values
   * @param executionStartedAt Timestamp when execution started (ISO 8601 string)
   * @param executionEndedAt Timestamp when execution ended (ISO 8601 string)
   * @param executionTimeMillis Execution time in milliseconds
   * @param queryId Optional query ID (defaults to hash of column names)
   * @return Tuple of (JsObject with Dune format data, row count, format string)
   */
  def formatAsDune(
    columnNames: Seq[String],
    columnTypes: Seq[String],
    rows: Seq[Seq[Any]],
    executionStartedAt: String,
    executionEndedAt: String,
    executionTimeMillis: Long,
    queryId: Option[Long] = None
  ): (JsValue, Int, String) = {
    import java.time.{ZonedDateTime, ZoneOffset}
    import java.time.format.DateTimeFormatter
    
    val rowCount = rows.length
    val datapointCount = columnNames.length
    
    // Calculate approximate result set bytes (rough estimate)
    val resultSetBytes = rows.map { row =>
      row.map { value =>
        if (value == null) 0
        else value.toString.getBytes("UTF-8").length
      }.sum
    }.sum
    
    // Generate execution ID (simple hash-based ID)
    val executionId = java.util.UUID.randomUUID().toString.replace("-", "").toUpperCase
    
    // Generate query ID if not provided
    val finalQueryId = queryId.getOrElse(columnNames.mkString("").hashCode.toLong)
    
    // Calculate expires_at (3 months from now)
    val expiresAt = ZonedDateTime.parse(executionEndedAt).plusMonths(3).format(DateTimeFormatter.ISO_INSTANT)
    
    // Format rows as objects with column names as keys
    val jsonRows = rows.map { row =>
      val fields = columnNames.zip(row).map { case (colName, value) =>
        (colName, toJsValue(value))
      }.toMap
      JsObject(fields)
    }

    val result = JsObject(
      "execution_ended_at" -> JsString(executionEndedAt),
      "execution_id" -> JsString(executionId),
      "execution_started_at" -> JsString(executionStartedAt),
      "expires_at" -> JsString(expiresAt),
      "is_execution_finished" -> JsBoolean(true),
      "query_id" -> JsNumber(finalQueryId),
      "result" -> JsObject(
        "metadata" -> JsObject(
          "column_names" -> JsArray(columnNames.map(JsString(_)).toVector),
          "column_types" -> JsArray(columnTypes.map(JsString(_)).toVector),
          "datapoint_count" -> JsNumber(datapointCount),
          "execution_time_millis" -> JsNumber(executionTimeMillis),
          "pending_time_millis" -> JsNumber(0),
          "result_set_bytes" -> JsNumber(resultSetBytes),
          "row_count" -> JsNumber(rowCount),
          "total_result_set_bytes" -> JsNumber(resultSetBytes),
          "total_row_count" -> JsNumber(rowCount)
        ),
        "rows" -> JsArray(jsonRows.toVector)
      ),
      "state" -> JsString("QUERY_STATE_COMPLETED"),
      "submitted_at" -> JsString(executionStartedAt)
    )

    (result, rowCount, "dune")
  }
}

class DataSourceSQL(uri0:String,fw:QueryGuard = QueryGuardAllow) extends DataSource {
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
  log.info(s"HikariConfig: ${hikariConfig.getJdbcUrl()} (${jdbcUrl})")
  private val dataSource = new HikariDataSource(hikariConfig)
  System.setProperty("user.timezone", tz)

  // Async connection pool
  private val asyncConnectionPool = {
    val asyncJdbcUrl = dbUri.getJdbcUrl(async = true)
    
    // Create appropriate connection pool based on database type
    dbUri.dbType match {
      case "postgres" | "postgresql" =>
        PostgreSQLConnectionBuilder.createConnectionPool(asyncJdbcUrl)
      case "mysql" =>
        MySQLConnectionBuilder.createConnectionPool(asyncJdbcUrl)
      case other =>
        throw new IllegalArgumentException(s"Unsupported database type for async execution: ${other}. Supported types: postgres, mysql")
    }
  }

  // Sync connection pool
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

    fw.isAllowed(sqlQuery, Map("lang" -> "sql")) match {
      case Success(true) => // Continue processing
      case Success(false) => 
        return Future.failed(new Exception("Query Rejected"))
      case Failure(e) => 
        return Future.failed(new Exception(s"Query validation failed: ${e.getMessage}"))
    }

    // Determine output format: prefer formatFromQuery, then req.fmt, then default to json
    val outputFormat = formatFromQuery.orElse(req.fmt).getOrElse("json")

    // Determine execution type (sync/async) from req.typ
    val executionType = req.typ.filter(!_.isBlank).getOrElse("sync")

    log.info(s"${tid}/${req.id}: Executing SQL (${executionType},${outputFormat}): '${sqlQuery}' -> ${jdbcUrl}")

    // Execute query based on type
    val f = executionType match {
      case "async" => executeAsync(req, sqlQuery, outputFormat, ts0)
      case _ => executeSync(req, sqlQuery, outputFormat, ts0)
    }

    // Log failures
    f.onComplete {
      case Failure(e) => 
        log.warn(s"${tid}/${req.id}: ${e.getMessage}")
      case Success(r) =>
        log.debug(s"${tid}/${req.id}: ${r}")
    }

    f
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
              val executionEndedAt = System.currentTimeMillis()
              val executionTimeMillis = executionEndedAt - ts0
              
              val (resultData, rowCount, format) = outputFormat match {
                case "csv" => formatResultSetAsCSV(resultSet)
                case "json2" => formatResultSetAsJSON2(resultSet)
                case "dune" => formatResultSetAsDune(resultSet, ts0, executionEndedAt, executionTimeMillis)
                case _ => formatResultSetAsJSON(resultSet)
              }

              val json = if (format == "dune") {
                // For dune format, wrap resultData in "data" field to match Dune API structure
                JsObject("data" -> resultData.asJsObject)
              } else {
                JsObject(
                  "query" -> JsString(sqlQuery),
                  "result" -> resultData,
                  "rows" -> JsNumber(rowCount)
                )
              }

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
      val executionEndedAt = System.currentTimeMillis()
      val executionTimeMillis = executionEndedAt - ts0
      
      val (resultData, rowCount, format) = outputFormat match {
        case "csv" => formatAsyncResultAsCSV(queryResult)
        case "json2" => formatAsyncResultAsJSON2(queryResult)
        case "dune" => formatAsyncResultAsDune(queryResult, ts0, executionEndedAt, executionTimeMillis)
        case "json" | _ => formatAsyncResultAsJSON(queryResult)
      }

      val json = if (format == "dune") {
        // For dune format, wrap resultData in "data" field to match Dune API structure
        JsObject("data" -> resultData.asJsObject)
      } else {
        JsObject(
          "query" -> JsString(sqlQuery),
          "result" -> resultData,
          "rows" -> JsNumber(rowCount)
        )
      }

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

  private def formatResultSetAsJSON2(rs: ResultSet): (JsValue, Int, String) = {
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

    DataSourceSQL.formatAsJSON2(columnNames, rows.toSeq)
  }

  private def formatResultSetAsDune(rs: ResultSet, ts0: Long, ts: Long, executionTimeMillis: Long): (JsValue, Int, String) = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount

    // Get column names and types
    val columnNames = (1 to columnCount).map(i => metaData.getColumnName(i))
    val columnTypes = (1 to columnCount).map { i =>
      val sqlType = metaData.getColumnType(i)
      val typeName = metaData.getColumnTypeName(i)
      val precision = metaData.getPrecision(i)
      val scale = metaData.getScale(i)
      
      // Format type name similar to Dune format
      if (scale > 0 && precision > 0) {
        s"$typeName($precision, $scale)"
      } else if (precision > 0) {
        s"$typeName($precision)"
      } else {
        typeName.toLowerCase
      }
    }

    // Extract all rows
    val rows = scala.collection.mutable.ListBuffer[Seq[Any]]()
    while (rs.next()) {
      val row = (1 to columnCount).map(i => rs.getObject(i))
      rows += row
    }

    // Format timestamps
    val executionStartedAt = ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(ts0),
      ZoneOffset.UTC
    ).format(DateTimeFormatter.ISO_INSTANT)
    
    val executionEndedAt = ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(ts),
      ZoneOffset.UTC
    ).format(DateTimeFormatter.ISO_INSTANT)

    DataSourceSQL.formatAsDune(
      columnNames,
      columnTypes,
      rows.toSeq,
      executionStartedAt,
      executionEndedAt,
      executionTimeMillis
    )
  }

  private def formatAsyncResultAsCSV(queryResult: QueryResult): (JsValue, Int, String) = {
    val rows = queryResult.getRows.asScala.toSeq    
    if (rows.isEmpty) {
      return (JsString(""), 0, "csv")
    }

    // Get column count from first row (RowData extends List)
    val columnCount = rows.head.size()
    // Generate column names as col_0, col_1, etc. since jasync doesn't expose column metadata
    //val columnNames = (0 until columnCount).map(i => s"col_$i")
    val columnNames = queryResult.getRows.columnNames().asScala.toSeq

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
    //val columnNames = (0 until columnCount).map(i => s"col_$i")
    val columnNames = queryResult.getRows.columnNames().asScala.toSeq

    // Convert RowData to Seq[Seq[Any]]
    val rowsData = rows.map { row =>
      (0 until columnCount).map(idx => row.get(idx))
    }

    DataSourceSQL.formatAsJSON(columnNames, rowsData)
  }

  private def formatAsyncResultAsJSON2(queryResult: QueryResult): (JsValue, Int, String) = {
    val rows = queryResult.getRows.asScala.toSeq

    // Get column count from first row (RowData extends List)
    val columnCount = if (rows.isEmpty) 0 else rows.head.size()
    // Generate column names as col_0, col_1, etc. since jasync doesn't expose column metadata
    //val columnNames = (0 until columnCount).map(i => s"col_$i")
    val columnNames = queryResult.getRows.columnNames().asScala.toSeq

    // Convert RowData to Seq[Seq[Any]]
    val rowsData = rows.map { row =>
      (0 until columnCount).map(idx => row.get(idx))
    }

    DataSourceSQL.formatAsJSON2(columnNames, rowsData)
  }

  private def formatAsyncResultAsDune(queryResult: QueryResult, ts0: Long, ts: Long, executionTimeMillis: Long): (JsValue, Int, String) = {
    val rows = queryResult.getRows.asScala.toSeq

    // Get column count from first row (RowData extends List)
    val columnCount = if (rows.isEmpty) 0 else rows.head.size()
    // Generate column names as col_0, col_1, etc. since jasync doesn't expose column metadata
    //val columnNames = (0 until columnCount).map(i => s"col_$i")
    val columnNames = queryResult.getRows.columnNames().asScala.toSeq
    
    // Infer column types from data (since jasync doesn't expose metadata)
    val columnTypes = if (rows.isEmpty) {
      Seq.fill(columnCount)("text")
    } else {
      (0 until columnCount).map { idx =>
        val sampleValue = rows.head.get(idx)
        sampleValue match {
          case _: java.lang.Integer => "integer"
          case _: java.lang.Long => "bigint"
          case _: java.lang.Double => "double"
          case _: java.lang.Float => "real"
          case _: java.math.BigDecimal => "decimal"
          case _: Boolean => "boolean"
          case _: String => "text"
          case null => "text"
          case _ => "text"
        }
      }
    }    

    // Convert RowData to Seq[Seq[Any]]
    val rowsData = rows.map { row =>
      (0 until columnCount).map(idx => row.get(idx))
    }

    // Format timestamps
    val executionStartedAt = ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(ts0),
      ZoneOffset.UTC
    ).format(DateTimeFormatter.ISO_INSTANT)
    
    val executionEndedAt = ZonedDateTime.ofInstant(
      java.time.Instant.ofEpochMilli(ts),
      ZoneOffset.UTC
    ).format(DateTimeFormatter.ISO_INSTANT)

    DataSourceSQL.formatAsDune(
      columnNames,
      columnTypes,
      rowsData,
      executionStartedAt,
      executionEndedAt,
      executionTimeMillis
    )
  }
}
