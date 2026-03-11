package io.syspulse.skel.wf.temporal

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class TemporalURISpec extends AnyWordSpec with Matchers {

  "TemporalURI" should {

    "use defaults for bare temporal:// URI" in {
      val t = TemporalURI("temporal://")
      t.host shouldBe t.DEF_HOST
      t.port shouldBe t.DEF_PORT
      t.namespace shouldBe t.DEF_NAMESPACE
      t.enableKeepAlive shouldBe t.DEF_ENABLE_KEEP_ALIVE
      t.keepAliveTime shouldBe t.DEF_KEEP_ALIVE_TIME
      t.keepAliveTimeout shouldBe t.DEF_KEEP_ALIVE_TIMEOUT
      t.rpcTimeout shouldBe t.DEF_RPC_TIMEOUT
      t.target shouldBe s"${t.DEF_HOST}:${t.DEF_PORT}"
    }

    "parse host, port and namespace from path" in {
      val t = TemporalURI("temporal://my-host:7235/prod")
      t.host shouldBe "my-host"
      t.port shouldBe 7235
      t.namespace shouldBe "prod"
      t.target shouldBe "my-host:7235"
    }

    "fallback to default port on invalid port" in {
      val t = TemporalURI("temporal://my-host:not-a-port/prod")
      t.host shouldBe "my-host"
      t.port shouldBe t.DEF_PORT
      t.namespace shouldBe "prod"
    }

    "prefer path namespace over query parameter" in {
      val t = TemporalURI("temporal://127.0.0.1:7233/default?namespace=prod")
      t.host shouldBe "127.0.0.1"
      t.port shouldBe 7233
      // path namespace \"default\" wins over query parameter
      t.namespace shouldBe "default"
    }

    "allow namespace from query parameter when no path namespace" in {
      val t = TemporalURI("temporal://?namespace=prod&rpc_timeout=30000")
      t.host shouldBe t.DEF_HOST
      t.port shouldBe t.DEF_PORT
      t.namespace shouldBe "prod"
      t.rpcTimeout shouldBe 30000L
    }

    "parse boolean and time options from query parameters" in {
      val t = TemporalURI(
        "temporal://127.0.0.1:7233/default?" +
          "enable_keep_alive=false&keep_alive_time=1000&keep_alive_timeout=2000&rpc_timeout=3000"
      )

      t.enableKeepAlive shouldBe false
      t.keepAliveTime shouldBe 1000L
      t.keepAliveTimeout shouldBe 2000L
      t.rpcTimeout shouldBe 3000L
    }

    "treat empty enable_keep_alive value as true" in {
      val t = TemporalURI("temporal://127.0.0.1:7233/default?enable_keep_alive")
      t.enableKeepAlive shouldBe true
    }

    "parse JWT auth token from query parameter" in {
      val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0In0.sig"
      val t = TemporalURI(s"temporal://localhost:7233?auth=$token")
      t.auth shouldBe Some(token)
    }

    "return None for auth when not specified" in {
      val t = TemporalURI("temporal://localhost:7233")
      t.auth shouldBe None
    }

    "return None for auth when empty" in {
      val t = TemporalURI("temporal://localhost:7233?auth=")
      t.auth shouldBe None
    }

    "parse auth token with other parameters" in {
      val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0In0.sig"
      val t = TemporalURI(s"temporal://my-host:7233/prod?auth=$token&rpc_timeout=30000")
      t.auth shouldBe Some(token)
      t.namespace shouldBe "prod"
      t.rpcTimeout shouldBe 30000L
    }

    "parse tls=ignore parameter" in {
      val t = TemporalURI("temporal://localhost:7233?tls=ignore")
      t.tlsInsecure shouldBe true
    }

    "default tls to false" in {
      val t = TemporalURI("temporal://localhost:7233")
      t.tlsInsecure shouldBe false
    }
    
  }
}

