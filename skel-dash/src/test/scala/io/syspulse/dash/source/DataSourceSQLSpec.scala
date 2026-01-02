package io.syspulse.dash.source

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Try,Success,Failure}
import scala.concurrent.{Future, Await, ExecutionContext}
import scala.concurrent.duration._

import spray.json._
import spray.json.DefaultJsonProtocol._

import io.syspulse.dash.server.DashData
import io.syspulse.dash.server.DashDataReq
import io.syspulse.skel.util.Util

import java.sql.{Connection, DriverManager}

class DataSourceSQLSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

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

  "DataSourceSQL" should {

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
      result.fmt shouldBe "table"
      result.data.fields("rows").convertTo[Int] shouldBe 2

      val resultStr = result.data.fields("result").convertTo[String]
      resultStr should include("test1")
      resultStr should include("test2")
      resultStr should include("100")
      resultStr should include("200")
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
      val resultStr = result.data.fields("result").convertTo[String]
      resultStr should include("foo")
      resultStr should include("42")
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
      val resultStr = result.data.fields("result").convertTo[String]
      resultStr should include("bar")
    }

    "return formatted table with correct structure" in {
      // Insert test data with various types
      val stmt = connection.createStatement()
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('alpha', 1)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('beta', 2)")
      stmt.execute("INSERT INTO test_sql_datasource (name, value) VALUES ('gamma', 3)")
      stmt.close()

      val req = DashDataReq(
        id = "test-table-format",
        src = "sql",
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY value"))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      val resultStr = result.data.fields("result").convertTo[String]

      // Should have header row
      resultStr should include("name")
      resultStr should include("value")

      // Should have separator line with dashes
      resultStr should include("---")
      resultStr should include("-+-")

      // Should have data rows
      resultStr should include("alpha")
      resultStr should include("beta")
      resultStr should include("gamma")

      // Check rows are in correct order
      val lines = resultStr.split("\n")
      lines.length should be > 3  // header + separator + at least 1 data row
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
      val resultStr = result.data.fields("result").convertTo[String]
      resultStr should include("(no rows)")
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
        query = Some(JsString("SELECT name, value FROM test_sql_datasource ORDER BY name"))
      )

      val future = dataSource.ask(req)
      val result = Await.result(future, 5.seconds)

      val resultStr = result.data.fields("result").convertTo[String]
      resultStr should include("NULL")
      result.data.fields("rows").convertTo[Int] shouldBe 2
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
