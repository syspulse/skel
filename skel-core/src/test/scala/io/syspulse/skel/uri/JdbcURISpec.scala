package io.syspulse.skel.uri

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import io.syspulse.skel.util.Util

class JdbcURISpec extends AnyWordSpec with Matchers {
  
  "JdbcURI" should {
    
    "parse URI with host and port" in {
      val uri = JdbcURI("postgres://localhost:5432/mydb")
      uri.host should === ("localhost")
      uri.port should === (5432)
      uri.dbType should === ("postgres")
      uri.db should === (Some("mydb"))
    }

    "parse URI with environment variable host containing port" in {
      // Set up environment variables for testing
      val env = Map(
        "DB_USER" -> "testuser",
        "DB_PASS" -> "testpass",
        "DB_HOST" -> "localhost:5555",
        "DB_NAME" -> "testdb"
      )
      
      // Temporarily replace Util.replaceEnvVar to use our test env
      val originalReplaceEnvVar = Util.replaceEnvVar _
      
      // Create URI string with env vars
      val uriString = "postgres://{DB_USER}:{DB_PASS}@{DB_HOST}/{DB_NAME}?TimeZone=UTC"
      
      // Manually resolve env vars for the test
      val resolvedUri = Util.replaceEnvVar(uriString, env)
      
      val uri = JdbcURI(resolvedUri)
      uri.host should === ("localhost")
      uri.port should === (5555)
      uri.dbType should === ("postgres")
      uri.user should === (Some("testuser"))
      uri.pass should === (Some("testpass"))
      uri.db should === (Some("testdb"))
      uri.timezone should === (Some("UTC"))
    }

    "parse URI with environment variable host without port (default port)" in {
      val env = Map(
        "DB_HOST" -> "localhost",
        "DB_NAME" -> "testdb"
      )
      
      val uriString = "postgres://{DB_HOST}/{DB_NAME}"
      val resolvedUri = Util.replaceEnvVar(uriString, env)
      
      val uri = JdbcURI(resolvedUri)
      uri.host should === ("localhost")
      uri.port should === (5432) // default postgres port
      uri.db should === (Some("testdb"))
    }

    "parse URI with credentials and host:port" in {
      val uri = JdbcURI("postgres://user:pass@localhost:5432/mydb")
      uri.host should === ("localhost")
      uri.port should === (5432)
      uri.user should === (Some("user"))
      uri.pass should === (Some("pass"))
      uri.db should === (Some("mydb"))
    }

    "parse URI with query parameters" in {
      val uri = JdbcURI("postgres://localhost:5432/mydb?TimeZone=UTC&ssl=true")
      uri.host should === ("localhost")
      uri.port should === (5432)
      uri.timezone should === (Some("UTC"))
      uri.opts should === (Map("TimeZone" -> "UTC", "ssl" -> "true"))
    }
  }
}
