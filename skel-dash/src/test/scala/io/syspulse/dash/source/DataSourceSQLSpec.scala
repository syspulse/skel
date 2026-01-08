package io.syspulse.skel.dash.source

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.ScalaFutures

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._

import spray.json._
import spray.json.DefaultJsonProtocol._

import io.syspulse.skel.dash.server.DashData
import io.syspulse.skel.dash.server.DashDataReq
import io.syspulse.skel.util.Util

import java.sql.{Connection, DriverManager}
import io.syspulse.skel.dash.source.DataSourceSQL

// Base trait with shared setup for both sync and async tests
trait DataSourceSQLTestBase extends Suite with BeforeAndAfterAll with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  // Load DB credentials from environment variables (sourced from env.local)
  val dbUser = sys.env.getOrElse("DB_USER", "dash_user")
  val dbPass = sys.env.getOrElse("DB_PASS", "dash_pass")
  val dbHost = "localhost:5432"
  val dbName = "dash_db"

  // Use simpler jdbc:// format with TimeZone parameter
  val jdbcUri = s"jdbc://${dbUser}:${dbPass}@${dbHost}/${dbName}?TimeZone=UTC"

  var dataSource: DataSourceSQL = _
  var connection: Connection = _

  override def beforeAll(): Unit = {
    super.beforeAll()

    // Initialize datasource (timezone from URI will be used)
    dataSource = new DataSourceSQL(jdbcUri)

    // Get connection for test setup using proper PostgreSQL JDBC URL
    val postgresJdbcUrl = s"jdbc:postgresql://${dbHost}/${dbName}"
    val props = new java.util.Properties()
    props.setProperty("user", dbUser)
    props.setProperty("password", dbPass)
    connection = DriverManager.getConnection(postgresJdbcUrl, props)

    // Set timezone for test connection
    val tzStmt = connection.createStatement()
    try {
      tzStmt.execute("SET TIME ZONE 'UTC'")
    } finally {
      tzStmt.close()
    }

    // Create test table
    val createTableSQL = """
      CREATE TABLE IF NOT EXISTS test_sql_datasource (
        id SERIAL PRIMARY KEY,
        name VARCHAR(100),
        value INTEGER,
        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
      )
    """
    val stmt = connection.createStatement()
    stmt.execute(createTableSQL)
    stmt.close()
  }

  override def afterAll(): Unit = {
    // Clean up test table
    try {
      val stmt = connection.createStatement()
      stmt.execute("DROP TABLE IF EXISTS test_sql_datasource")
      stmt.close()
      connection.close()
    } catch {
      case e: Exception => // Ignore cleanup errors
    }
    super.afterAll()
  }

  override def beforeEach(): Unit = {
    // Clear test data before each test
    val stmt = connection.createStatement()
    stmt.execute("DELETE FROM test_sql_datasource")
    stmt.close()
  }
}

// Sync tests suite
class DataSourceSQLSyncSpec extends AnyWordSpec with Matchers with DataSourceSQLTestBase {

  "DataSourceSQL (sync)" should {

    "return correct src identifier" in {
      dataSource.src shouldBe "sql"
    }

    "execute simple SELECT query with JsString query format" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('test1', 100)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('test2', 200)")
      stmt.close()

      val req = DashDataReq(
        id = "test-query-1",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY name"))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.id shouldBe "test-query-1"
      result.src shouldBe "sql"
      result.fmt shouldBe "json"
      result.data.fields("rows").convertTo[Int] shouldBe 2

      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      resultArray.elements.length shouldBe 2

      val row1 = resultArray.elements(0).asJsObject
      row1.fields("name").convertTo[String] shouldBe "test1"
      row1.fields("value").convertTo[Int] shouldBe 100

      val row2 = resultArray.elements(1).asJsObject
      row2.fields("name").convertTo[String] shouldBe "test2"
      row2.fields("value").convertTo[Int] shouldBe 200
    }

    "execute SELECT query with JsObject query format (query field)" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('foo', 42)")
      stmt.close()

      val req = DashDataReq(
        id = "test-query-2",
        src = "sql",
        query = Some(JsObject(
          "query" -> JsString("SELECT * FROM test_sql_datasource WHERE name = 'foo'")
        ))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.data.fields("rows").convertTo[Int] shouldBe 1
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      val row = resultArray.elements(0).asJsObject
      row.fields("name").convertTo[String] shouldBe "foo"
      row.fields("value").convertTo[Int] shouldBe 42
    }

    "execute SELECT query with JsObject query format (sql field)" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('bar', 99)")
      stmt.close()

      val req = DashDataReq(
        id = "test-query-3",
        src = "sql",
        query = Some(JsObject(
          "sql" -> JsString("SELECT name, value FROM test_sql_datasource WHERE value > 50")
        ))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.data.fields("rows").convertTo[Int] shouldBe 1
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      val row = resultArray.elements(0).asJsObject
      row.fields("name").convertTo[String] shouldBe "bar"
    }

    "return formatted JSON with correct structure" in {
      // Insert test data with various types
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('alpha', 1)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('beta', 2)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('gamma', 3)")
      stmt.close()

      val req = DashDataReq(
        id = "test-json-format",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY value"))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.fmt shouldBe "json"
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      resultArray.elements.length shouldBe 3

      // Check rows are in correct order
      val row1 = resultArray.elements(0).asJsObject
      row1.fields("name").convertTo[String] shouldBe "alpha"
      row1.fields("value").convertTo[Int] shouldBe 1

      val row2 = resultArray.elements(1).asJsObject
      row2.fields("name").convertTo[String] shouldBe "beta"
      row2.fields("value").convertTo[Int] shouldBe 2

      val row3 = resultArray.elements(2).asJsObject
      row3.fields("name").convertTo[String] shouldBe "gamma"
      row3.fields("value").convertTo[Int] shouldBe 3
    }

    "handle empty result set" in {
      val req = DashDataReq(
        id = "test-empty",
        src = "sql",
        query = Some(JsString("SELECT * FROM test_sql_datasource WHERE value > 999999"))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.data.fields("rows").convertTo[Int] shouldBe 0
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      resultArray.elements.length shouldBe 0
    }

    "handle NULL values in results" in {
      // Insert data with NULL
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES (NULL, 123)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('notNull', NULL)")
      stmt.close()

      val req = DashDataReq(
        id = "test-nulls",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY name NULLS FIRST"))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.data.fields("rows").convertTo[Int] shouldBe 2
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]

      // Check for JsNull values (NULL name comes first with NULLS FIRST)
      val row1 = resultArray.elements(0).asJsObject
      row1.fields("name") shouldBe JsNull
      row1.fields("value").convertTo[Int] shouldBe 123

      val row2 = resultArray.elements(1).asJsObject
      row2.fields("name").convertTo[String] shouldBe "notNull"
      row2.fields("value") shouldBe JsNull
    }

    "return CSV format when requested" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('csv1', 10)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('csv2', 20)")
      stmt.close()

      val req = DashDataReq(
        id = "test-csv",
        src = "sql",
        query = Some(JsObject(
          "query" -> JsString("SELECT name, value FROM test_sql_datasource ORDER BY name"),
          "format" -> JsString("csv")
        ))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.fmt shouldBe "csv"
      result.data.fields("rows").convertTo[Int] shouldBe 2

      val csvStr = result.data.fields("result").convertTo[String]
      // Check header
      csvStr should include("name,value")
      // Check data rows
      csvStr should include("csv1,10")
      csvStr should include("csv2,20")
    }

    "reject query with wrong datasource type" in {
      val req = DashDataReq(
        id = "test-wrong-src",
        src = "wrong-source",
        query = Some(JsString("SELECT 1"))
      )

      val future = dataSource.ask(req)

      assertThrows[Exception] {
        Await.result(future, 5.seconds)
      }
    }

    "fail gracefully on invalid SQL syntax" in {
      val req = DashDataReq(
        id = "test-invalid-sql",
        src = "sql",
        query = Some(JsString("INVALID SQL QUERY"))
      )

      val future = dataSource.ask(req)

      assertThrows[Exception] {
        Await.result(future, 5.seconds)
      }
    }

    "fail when query is missing" in {
      val req = DashDataReq(
        id = "test-no-query",
        src = "sql",
        query = None
      )

      val future = dataSource.ask(req)

      assertThrows[Exception] {
        Await.result(future, 5.seconds)
      }
    }

    "fail when query format is invalid" in {
      val req = DashDataReq(
        id = "test-invalid-format",
        src = "sql",
        query = Some(JsObject("invalid" -> JsString("no sql or query field")))
      )

      val future = dataSource.ask(req)

      assertThrows[Exception] {
        Await.result(future, 5.seconds)
      }
    }

    "include query in response data" in {
      val testQuery = "SELECT 1 as test_column"
      val req = DashDataReq(
        id = "test-query-in-response",
        src = "sql",
        query = Some(JsString(testQuery))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      result.data.fields("query").convertTo[String] shouldBe testQuery
    }

    "handle concurrent queries" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('concurrent1', 1)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('concurrent2', 2)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('concurrent3', 3)")
      stmt.close()

      val futures = (1 to 5).map { i =>
        val req = DashDataReq(
          id = s"test-concurrent-$i",
          src = "sql",
          query = Some(JsString(s"SELECT * FROM test_sql_datasource WHERE value = $i"))
        )
        dataSource.ask(req)
      }

      val results = Await.result(Future.sequence(futures), 10.seconds)

      results.length shouldBe 5
      results.zipWithIndex.foreach { case (result, idx) =>
        result.id shouldBe s"test-concurrent-${idx + 1}"
        result.src shouldBe "sql"
      }
    }

    "set correct timestamps" in {
      val req = DashDataReq(
        id = "test-timestamps",
        src = "sql",
        query = Some(JsString("SELECT 1"))
      )

      val before = System.currentTimeMillis()
      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)
      val after = System.currentTimeMillis()

      result.ts0 should be >= before
      result.ts should be >= result.ts0
      result.ts should be <= after
    }
  }
}

// Async tests suite
class DataSourceSQLAsyncSpec extends AnyWordSpec with Matchers with ScalaFutures with DataSourceSQLTestBase {
  override implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = 10.seconds, interval = 100.millis)

  "DataSourceSQL (async)" should {

    "return correct src identifier" in {
      dataSource.src shouldBe "sql"
    }

    "execute simple SELECT query with JsString query format" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('test1', 100)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('test2', 200)")
      stmt.close()

      val req = DashDataReq(
        id = "test-query-1-async",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY name")),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.id shouldBe "test-query-1-async"
      result.src shouldBe "sql"
      result.fmt shouldBe "json"
      result.data.fields("rows").convertTo[Int] shouldBe 2

      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      resultArray.elements.length shouldBe 2

      // Async results use generic column names (col_0, col_1, etc.) since jasync doesn't provide column metadata
      val row1 = resultArray.elements(0).asJsObject
      row1.fields("col_0").convertTo[String] shouldBe "test1"  // name column
      row1.fields("col_1").convertTo[Int] shouldBe 100        // value column

      val row2 = resultArray.elements(1).asJsObject
      row2.fields("col_0").convertTo[String] shouldBe "test2"  // name column
      row2.fields("col_1").convertTo[Int] shouldBe 200         // value column
    }

    "execute SELECT query with JsObject query format (query field)" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('foo', 42)")
      stmt.close()

      val req = DashDataReq(
        id = "test-query-2-async",
        src = "sql",
        query = Some(JsObject(
          "query" -> JsString("SELECT * FROM test_sql_datasource WHERE name = 'foo'")
        )),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.data.fields("rows").convertTo[Int] shouldBe 1
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      val row = resultArray.elements(0).asJsObject
      // Async results use generic column names: col_0=id, col_1=name, col_2=value, col_3=created_at
      row.fields("col_1").convertTo[String] shouldBe "foo"  // name column
      row.fields("col_2").convertTo[Int] shouldBe 42        // value column
    }

    "execute SELECT query with JsObject query format (sql field)" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('bar', 99)")
      stmt.close()

      val req = DashDataReq(
        id = "test-query-3-async",
        src = "sql",
        query = Some(JsObject(
          "sql" -> JsString("SELECT name, value FROM test_sql_datasource WHERE value > 50")
        )),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.data.fields("rows").convertTo[Int] shouldBe 1
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      val row = resultArray.elements(0).asJsObject
      // Async results use generic column names: col_0=name, col_1=value
      row.fields("col_0").convertTo[String] shouldBe "bar"
    }

    "return formatted JSON with correct structure" in {
      // Insert test data with various types
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('alpha', 1)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('beta', 2)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('gamma', 3)")
      stmt.close()

      val req = DashDataReq(
        id = "test-json-format-async",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY value")),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.fmt shouldBe "json"
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      resultArray.elements.length shouldBe 3

      // Check rows are in correct order (async uses generic column names)
      val row1 = resultArray.elements(0).asJsObject
      row1.fields("col_0").convertTo[String] shouldBe "alpha"
      row1.fields("col_1").convertTo[Int] shouldBe 1

      val row2 = resultArray.elements(1).asJsObject
      row2.fields("col_0").convertTo[String] shouldBe "beta"
      row2.fields("col_1").convertTo[Int] shouldBe 2

      val row3 = resultArray.elements(2).asJsObject
      row3.fields("col_0").convertTo[String] shouldBe "gamma"
      row3.fields("col_1").convertTo[Int] shouldBe 3
    }

    "handle empty result set" in {
      val req = DashDataReq(
        id = "test-empty-async",
        src = "sql",
        query = Some(JsString("SELECT * FROM test_sql_datasource WHERE value > 999999")),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.data.fields("rows").convertTo[Int] shouldBe 0
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]
      resultArray.elements.length shouldBe 0
    }

    "handle NULL values in results" in {
      // Insert data with NULL
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES (NULL, 123)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('notNull', NULL)")
      stmt.close()

      val req = DashDataReq(
        id = "test-nulls-async",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY name NULLS FIRST")),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.data.fields("rows").convertTo[Int] shouldBe 2
      val resultArray = result.data.fields("result").asInstanceOf[JsArray]

      // Check for JsNull values (NULL name comes first with NULLS FIRST)
      // Async uses generic column names: col_0=name, col_1=value
      val row1 = resultArray.elements(0).asJsObject
      row1.fields("col_0") shouldBe JsNull
      row1.fields("col_1").convertTo[Int] shouldBe 123

      val row2 = resultArray.elements(1).asJsObject
      row2.fields("col_0").convertTo[String] shouldBe "notNull"
      row2.fields("col_1") shouldBe JsNull
    }

    "return CSV format when requested" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('csv1', 10)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('csv2', 20)")
      stmt.close()

      val req = DashDataReq(
        id = "test-csv-async",
        src = "sql",
        query = Some(JsObject(
          "query" -> JsString("SELECT name, value FROM test_sql_datasource ORDER BY name"),
          "format" -> JsString("csv")
        )),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.fmt shouldBe "csv"
      result.data.fields("rows").convertTo[Int] shouldBe 2

      val csvStr = result.data.fields("result").convertTo[String]
      // Check header (async uses generic column names)
      csvStr should include("col_0,col_1")
      // Check data rows
      csvStr should include("csv1,10")
      csvStr should include("csv2,20")
    }

    "reject query with wrong datasource type" in {
      val req = DashDataReq(
        id = "test-wrong-src-async",
        src = "wrong-source",
        query = Some(JsString("SELECT 1")),
        typ = Some("async")
      )

      val future = dataSource.ask(req)

      future.failed.futureValue shouldBe a[Exception]
    }

    "fail gracefully on invalid SQL syntax" in {
      val req = DashDataReq(
        id = "test-invalid-sql-async",
        src = "sql",
        query = Some(JsString("INVALID SQL QUERY")),
        typ = Some("async")
      )

      val future = dataSource.ask(req)

      future.failed.futureValue shouldBe a[Exception]
    }

    "fail when query is missing" in {
      val req = DashDataReq(
        id = "test-no-query-async",
        src = "sql",
        query = None,
        typ = Some("async")
      )

      val future = dataSource.ask(req)

      future.failed.futureValue shouldBe a[Exception]
    }

    "fail when query format is invalid" in {
      val req = DashDataReq(
        id = "test-invalid-format-async",
        src = "sql",
        query = Some(JsObject("invalid" -> JsString("no sql or query field"))),
        typ = Some("async")
      )

      val future = dataSource.ask(req)

      future.failed.futureValue shouldBe a[Exception]
    }

    "include query in response data" in {
      val testQuery = "SELECT 1 as test_column"
      val req = DashDataReq(
        id = "test-query-in-response-async",
        src = "sql",
        query = Some(JsString(testQuery)),
        typ = Some("async")
      )

      val future = dataSource.ask(req)
      val result = future.futureValue

      result.data.fields("query").convertTo[String] shouldBe testQuery
    }

    "handle concurrent async queries" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('concurrent1', 1)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('concurrent2', 2)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('concurrent3', 3)")
      stmt.close()

      val futures = (1 to 5).map { i =>
        val req = DashDataReq(
          id = s"test-concurrent-async-$i",
          src = "sql",
          query = Some(JsString(s"SELECT * FROM test_sql_datasource WHERE value = $i")),
          typ = Some("async")
        )
        dataSource.ask(req)
      }

      val results = Future.sequence(futures).futureValue

      results.length shouldBe 5
      results.zipWithIndex.foreach { case (result, idx) =>
        result.id shouldBe s"test-concurrent-async-${idx + 1}"
        result.src shouldBe "sql"
      }
    }

    "set correct timestamps" in {
      val req = DashDataReq(
        id = "test-timestamps-async",
        src = "sql",
        query = Some(JsString("SELECT 1")),
        typ = Some("async")
      )

      val before = System.currentTimeMillis()
      val future = dataSource.ask(req)
      val result = future.futureValue
      val after = System.currentTimeMillis()

      result.ts0 should be >= before
      result.ts should be >= result.ts0
      result.ts should be <= after
    }

    "execute async queries without blocking" in {
      // Insert test data
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('nonblocking', 999)")
      stmt.close()

      val req = DashDataReq(
        id = "test-nonblocking-async",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource WHERE value = 999")),
        typ = Some("async")
      )

      val startTime = System.currentTimeMillis()
      val future = dataSource.ask(req)
      val futureCreatedTime = System.currentTimeMillis()
      
      // Future creation should be nearly instantaneous (non-blocking)
      val creationDuration = futureCreatedTime - startTime
      creationDuration should be < 100L

      // Now wait for the result
      val result = future.futureValue
      val endTime = System.currentTimeMillis()

      result.id shouldBe "test-nonblocking-async"
      result.data.fields("rows").convertTo[Int] shouldBe 1
      
      // Verify the query actually executed (took some time)
      val executionDuration = endTime - futureCreatedTime
      executionDuration should be > 0L
    }
  }
}
