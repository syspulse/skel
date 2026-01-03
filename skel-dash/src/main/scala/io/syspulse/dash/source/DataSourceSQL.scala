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

object DataSourceSQL {
  val DEFAULT_MAX_POOL_SIZE = 4
  val DEFAULT_MIN_IDLE = 1
  val DEFAULT_CONNECTION_TIMEOUT = 30000L
}

class DataSourceSQL(uri0:String) extends DataSource {
  private val log = Logger(this.getClass)

  val dbUri = JdbcURI(uri0)

  // Use JdbcURI's jdbcUrl method (includes query parameters like ?TimeZone=UTC)
  val jdbcUrl = dbUri.jdbcUrl

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

  // Helper method to get connection from pool
  private def getConnection(): Connection = {
    dataSource.getConnection()    
  }

  log.info(s"DataSourceSQL: '${uri0}' -> ${jdbcUrl}")

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

  def src: String = "sql"  

  def ask(req:DashDataReq, tid:Option[String] = None): Future[DashData] = {
    if(req.src != this.src) {
      return Future.failed(new Exception(s"unsupported datasource: '${req.src}'"))
    } 

    val ts0 = System.currentTimeMillis()

    // Extract SQL query from req.query
    val sqlQuery = req.query match {
      case Some(JsString(q)) => q
      case Some(JsObject(fields)) =>
        // Try "query" field first
        fields.get("query") match {
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
      case _ => return Future.failed(new Exception("SQL query not provided in request.query"))
    }

    // Determine output format from query or use default (json)
    val outputFormat = req.fmt.getOrElse("json")

    log.info(s"Executing SQL query: '${sqlQuery}' -> ${jdbcUrl} (format: ${outputFormat})")

    // Execute query and format result
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

  private def formatResultSetAsCSV(rs: ResultSet): (JsValue, Int, String) = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount

    // Get column names
    val columnNames = (1 to columnCount).map(i => metaData.getColumnName(i))

    // Build CSV
    val sb = new StringBuilder

    // Header row
    sb.append(columnNames.mkString(",")).append("\n")

    // Data rows
    var rowCount = 0
    while (rs.next()) {
      val row = (1 to columnCount).map { i =>
        val value = rs.getObject(i)
        val str = if (value == null) "" else value.toString
        // Escape CSV values that contain comma, quote, or newline
        if (str.contains(",") || str.contains("\"") || str.contains("\n")) {
          "\"" + str.replace("\"", "\"\"") + "\""
        } else {
          str
        }
      }
      sb.append(row.mkString(",")).append("\n")
      rowCount += 1
    }

    (JsString(sb.toString()), rowCount, "csv")
  }

  private def formatResultSetAsJSON(rs: ResultSet): (JsValue, Int, String) = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount

    // Get column names
    val columnNames = (1 to columnCount).map(i => metaData.getColumnName(i))

    // Build array of JSON objects
    val rows = scala.collection.mutable.ListBuffer[JsObject]()
    while (rs.next()) {
      val fields = columnNames.zipWithIndex.map { case (colName, idx) =>
        val value = rs.getObject(idx + 1)
        val jsValue = if (value == null) {
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
        (colName, jsValue)
      }.toMap
      rows += JsObject(fields)
    }

    (JsArray(rows.toVector), rows.length, "json")
  }
}
