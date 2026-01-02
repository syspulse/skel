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

class DataSourceSQL(uri0:String) extends DataSource {
  private val log = Logger(this.getClass)

  val dbUri = JdbcURI(uri0)

  // Use JdbcURI's jdbcUrl method and append timezone parameter
  val jdbcUrl = dbUri.jdbcUrl

  // Configure HikariCP with connection initialization SQL
  private val hikariConfig = {
    val config = new HikariConfig()
    config.setJdbcUrl(jdbcUrl)
    if (dbUri.user.isDefined) config.setUsername(dbUri.user.get)
    if (dbUri.pass.isDefined) config.setPassword(dbUri.pass.get)

    // // Set datasource properties to configure timezone during connection establishment
    // // This sends TimeZone=UTC to PostgreSQL during the initial handshake
    config.addDataSourceProperty("TimeZone", "UTC")

    // // Set connection initialization SQL as backup
    // config.setConnectionInitSql("SET TIME ZONE 'UTC'")

    // Connection pool settings
    config.setMaximumPoolSize(4)
    config.setMinimumIdle(1)
    config.setConnectionTimeout(30000)

    config
  }

  val tz = System.getProperty("user.timezone")
  System.setProperty("user.timezone", "UTC")
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

    log.info(s"Executing SQL query: '${sqlQuery.take(100)}' (${jdbcUrl})")

    // Execute query and format as table
    Future {
      blocking {
        val connection = getConnection()
        try {
          val statement = connection.createStatement()
          try {
            val resultSet = statement.executeQuery(sqlQuery)
            try {
              val (tableStr, rowCount) = formatResultSetAsTable(resultSet)
              val json = JsObject(
                "query" -> JsString(sqlQuery),
                "result" -> JsString(tableStr),
                "rows" -> JsNumber(rowCount)
              )
              
              DashData(
                id = req.id,
                src = src,
                fmt = "table",
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

  private def formatResultSetAsTable(rs: ResultSet): (String, Int) = {
    val metaData = rs.getMetaData
    val columnCount = metaData.getColumnCount
    
    // Get column names
    val columnNames = (1 to columnCount).map(i => metaData.getColumnName(i))
    
    // Get all rows
    val rows = scala.collection.mutable.ListBuffer[Vector[String]]()
    while (rs.next()) {
      val row = (1 to columnCount).map { i =>
        val value = rs.getObject(i)
        if (value == null) "NULL" else value.toString
      }.toVector
      rows += row
    }
    
    val rowCount = rows.length
    
    // Calculate column widths
    val columnWidths = columnNames.zipWithIndex.map { case (name, idx) =>
      val nameWidth = name.length
      val maxDataWidth = if (rows.nonEmpty) rows.map(_(idx).length).foldLeft(0)(Math.max) else 0
      Math.max(nameWidth, maxDataWidth).max(3) // Minimum width of 3
    }
    
    // Build table string
    val sb = new StringBuilder
    
    // Header
    val headerRow = columnNames.zipWithIndex.map { case (name, idx) =>
      name.padTo(columnWidths(idx), ' ')
    }.mkString(" | ")
    sb.append(headerRow).append("\n")
    
    // Separator
    val separator = columnWidths.map("-" * _).mkString("-+-")
    sb.append(separator).append("\n")
    
    // Data rows
    if (rows.isEmpty) {
      sb.append("(no rows)").append("\n")
    } else {
      rows.foreach { row =>
        val rowStr = row.zipWithIndex.map { case (value, idx) =>
          value.padTo(columnWidths(idx), ' ')
        }.mkString(" | ")
        sb.append(rowStr).append("\n")
      }
    }
    
    (sb.toString(), rowCount)
  }
}
