package io.syspulse.skel.uri

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class RedisURISpec extends AnyWordSpec with Matchers {
  
  "RedisURI" should {
    
    "parse basic URI with host and port" in {
      val uri = RedisURI("redis://localhost:6379")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.db should === (0)
      uri.user should === (None)
      uri.pass should === (None)
      uri.subscription should === (None)
      uri.ops should === (Map())
    }

    "parse URI with only host (default port)" in {
      val uri = RedisURI("redis://localhost")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.db should === (0)
    }

    "parse URI with credentials" in {
      val uri = RedisURI("redis://user:pass@localhost:6379")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.user should === (Some("user"))
      uri.pass should === (Some("pass"))
      uri.db should === (0)
    }

    "parse URI with database index" in {
      val uri = RedisURI("redis://localhost:6379/1")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.db should === (1)
      uri.subscription should === (None)
    }

    "parse URI with database index and subscription channel" in {
      val uri = RedisURI("redis://localhost:6379/1/mychannel")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.db should === (1)
      uri.subscription should === (Some("mychannel"))
    }

    "parse URI with query parameters" in {
      val uri = RedisURI("redis://localhost:6379?timeout=5000&key1=value1")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.timeout should === (5000L)
      uri.ops should === (Map("timeout" -> "5000", "key1" -> "value1"))
    }

    "parse URI with credentials, database, and query parameters" in {
      val uri = RedisURI("redis://user:pass@localhost:6379/2?timeout=3000")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.user should === (Some("user"))
      uri.pass should === (Some("pass"))
      uri.db should === (2)
      uri.timeout should === (3000L)
      uri.ops should === (Map("timeout" -> "3000"))
    }

    "parse URI with credentials, database, channel, and query parameters" in {
      val uri = RedisURI("redis://user:pass@localhost:6379/2/mychannel?timeout=5000")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.user should === (Some("user"))
      uri.pass should === (Some("pass"))
      uri.db should === (2)
      uri.subscription should === (Some("mychannel"))
      uri.timeout should === (5000L)
    }

    "use default timeout when not specified" in {
      val uri = RedisURI("redis://localhost:6379")
      uri.timeout should === (RedisURI.DEF_TIMEOUT)
    }

    "use default values for empty URI" in {
      val uri = RedisURI("redis://")
      uri.host should === ("localhost")
      uri.port should === (6379)
      uri.db should === (0)
    }

    "handle different host and port" in {
      val uri = RedisURI("redis://example.com:6380")
      uri.host should === ("example.com")
      uri.port should === (6380)
    }

    "handle URI with only user (no password)" in {
      val uri = RedisURI("redis://user@localhost:6379")
      uri.host should === ("localhost")
      uri.port should === (6379)
      // When only user is provided without password, both should be None based on parsing logic
      uri.user should === (None)
      uri.pass should === (None)
    }

    "parse URI with multiple query parameters" in {
      val uri = RedisURI("redis://localhost:6379?timeout=5000&retry=3&pool=10")
      uri.ops should === (Map("timeout" -> "5000", "retry" -> "3", "pool" -> "10"))
      uri.timeout should === (5000L)
    }
  }
}

