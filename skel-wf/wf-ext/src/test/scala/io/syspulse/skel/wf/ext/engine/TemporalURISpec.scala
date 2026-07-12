package io.syspulse.skel.wf.ext.engine

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class TemporalURISpec extends AnyWordSpec with Matchers {

  "TemporalURI (engine)" should {

    "use defaults for bare temporal:// URI" in {
      val t = TemporalURI("temporal://")
      t.host shouldBe t.DEF_HOST
      t.port shouldBe t.DEF_PORT
      t.namespace shouldBe t.DEF_NAMESPACE
      t.target shouldBe s"${t.DEF_HOST}:${t.DEF_PORT}"
    }

    "parse host, port and namespace from path" in {
      val t = TemporalURI("temporal://127.0.0.1:7233/default")
      t.host shouldBe "127.0.0.1"
      t.port shouldBe 7233
      t.namespace shouldBe "default"
      t.target shouldBe "127.0.0.1:7233"
    }

    "fall back to default port on invalid port" in {
      val t = TemporalURI("temporal://my-host:not-a-port/prod")
      t.port shouldBe t.DEF_PORT
      t.namespace shouldBe "prod"
    }

    "recognise wildcard namespace" in {
      val t = TemporalURI("temporal://127.0.0.1:7233/*")
      t.isAllNamespaces shouldBe true
    }

    "split a comma-separated namespace list" in {
      val t = TemporalURI("temporal://127.0.0.1:7233/ns1,ns2,ns3")
      t.isAllNamespaces shouldBe false
      t.namespaceList shouldBe Seq("ns1", "ns2", "ns3")
    }

    "parse tls and auth options" in {
      val t = TemporalURI("temporal://secure.temporal.io:7233?tls=cert&auth=tok123")
      t.tlsSecure shouldBe true
      t.tlsInsecure shouldBe false
      t.auth shouldBe Some("tok123")
    }

    "return None auth when absent or empty" in {
      TemporalURI("temporal://localhost:7233").auth shouldBe None
      TemporalURI("temporal://localhost:7233?auth=").auth shouldBe None
    }
  }
}
