package io.syspulse.skel.uri

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ElasticURISpec extends AnyWordSpec with Matchers {
  
  "ElasticURI" should {
    "parse basic URI" in {
      val uri = ElasticURI("es://localhost:9200/index")
      uri.url should === ("http://localhost:9200")
      uri.index should === ("index")
      uri.user should === (None)
      uri.pass should === (None)
      uri.ops should === (Map())
    }

    "parse URI with credentials" in {
      val uri = ElasticURI("es://user:pass@localhost:9200/index")
      uri.url should === ("http://localhost:9200")
      uri.index should === ("index")
      uri.user should === (Some("user"))
      uri.pass should === (Some("pass"))
      uri.ops should === (Map())
    }

    "parse URI with query parameters" in {
      val uri = ElasticURI("es://localhost:9200/index?key1=value1&key2=value2")
      uri.url should === ("http://localhost:9200")
      uri.index should === ("index")
      uri.ops should === (Map("key1" -> "value1", "key2" -> "value2"))
    }

    "parse URI with credentials and query parameters" in {
      val uri = ElasticURI("es://user:pass@localhost:9200/index?key1=value1&key2=value2")
      uri.url should === ("http://localhost:9200")
      uri.index should === ("index")
      uri.user should === (Some("user"))
      uri.pass should === (Some("pass"))
      uri.ops should === (Map("key1" -> "value1", "key2" -> "value2"))
    }

    "use default values when index is missing" in {
      val uri = ElasticURI("es://localhost:9200")
      uri.url should === ("http://localhost:9200")
      uri.index should === ("index")
    }

    
    "handle malformed URIs gracefully" in {
      val uri = ElasticURI("ess://")
      uri.url should === ("https://localhost:9300")
      uri.index should === ("index")
      uri.user should === (None)
      uri.pass should === (None)
    }

    "parse URI with credentials and query parameters TLS" in {
      val uri = ElasticURI("ess://user:pass@host1/")
      uri.url should === ("https://host1/")
      uri.user should === (Some("user"))
      uri.pass should === (Some("pass"))      
    }
  }
} 