package io.syspulse.skel.uri

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class CoingeckoURISpec extends AnyWordSpec with Matchers {

  "CoingeckoURI" should {

    "parse apiKey only" in {
      val u = CoingeckoURI("coingecko://myKey")
      u.apiKey shouldBe "myKey"
      u.max shouldBe 10
      u.ops shouldBe Map.empty
    }

    "parse apiKey with max" in {
      val u = CoingeckoURI("coingecko://myKey?max=25")
      u.apiKey shouldBe "myKey"
      u.max shouldBe 25
      u.ops should contain ("max" -> "25")
      u.getBaseUrl() shouldBe "https://pro-api.coingecko.com/api/v3"
    }

    "fallback to env when missing apiKey" in {
      val key = sys.env.getOrElse("COINGECKO_API_KEY", "")
      val u = CoingeckoURI("coingecko://")
      u.apiKey shouldBe key
      u.max shouldBe 10
      u.getBaseUrl() shouldBe "https://pro-api.coingecko.com/api/v3"
    }

    "preserve extra ops" in {
      val u = CoingeckoURI("coingecko://k?foo=bar&max=7&x=1")
      u.apiKey shouldBe "k"
      u.max shouldBe 7
      u.ops should contain allOf ("foo" -> "bar", "x" -> "1", "max" -> "7")
      u.getBaseUrl() shouldBe "https://pro-api.coingecko.com/api/v3"
    }

    // Tests for cg:// prefix (free tier)
    "parse cg:// prefix with apiKey" in {
      val u = CoingeckoURI("cg://myFreeKey")
      u.apiKey shouldBe "myFreeKey"
      u.max shouldBe 10
      u.ops shouldBe Map.empty
      u.getBaseUrl() shouldBe "https://api.coingecko.com/api/v3"
    }

    "parse cg:// prefix with apiKey and max" in {
      val u = CoingeckoURI("cg://myFreeKey?max=15")
      u.apiKey shouldBe "myFreeKey"
      u.max shouldBe 15
      u.ops should contain ("max" -> "15")
      u.getBaseUrl() shouldBe "https://api.coingecko.com/api/v3"
    }

    "parse cg:// prefix with apiKey and multiple ops" in {
      val u = CoingeckoURI("cg://myFreeKey?max=20&throttle=5000&timeout=15000")
      u.apiKey shouldBe "myFreeKey"
      u.max shouldBe 20
      u.throttle shouldBe 5000L
      u.timeout.toMillis shouldBe 15000L
      u.ops should contain allOf ("max" -> "20", "throttle" -> "5000", "timeout" -> "15000")
      u.getBaseUrl() shouldBe "https://api.coingecko.com/api/v3"
    }

    "parse cg:// prefix without apiKey (fallback to env)" in {
      val key = sys.env.getOrElse("CG_API_KEY", "")
      val u = CoingeckoURI("cg://")
      u.apiKey shouldBe key
      u.max shouldBe 10
      u.getBaseUrl() shouldBe "https://api.coingecko.com/api/v3"
    }

    // Tests for http://localhost:3000 style URIs
    "parse http://localhost:3000 style URI" in {
      val u = CoingeckoURI("http://localhost:3000")
      u.apiKey shouldBe ""
      u.max shouldBe 10
      u.ops shouldBe Map.empty
      u.getBaseUrl() shouldBe "http://localhost:3000"
    }

    "parse http://localhost:3000 with apiKey in ops" in {
      val u = CoingeckoURI("http://localhost:3000?apiKey=localKey")
      u.apiKey shouldBe "localKey"
      u.max shouldBe 10
      u.ops should contain ("apiKey" -> "localKey")
      u.getBaseUrl() shouldBe "http://localhost:3000"
    }

    "parse http://localhost:3000 with apiKey in ops and max" in {
      val u = CoingeckoURI("http://localhost:3000?apiKey=localKey&max=25")
      u.apiKey shouldBe "localKey"
      u.max shouldBe 25
      u.ops should contain allOf ("apiKey" -> "localKey", "max" -> "25")
      u.getBaseUrl() shouldBe "http://localhost:3000"
    }

    "parse http://localhost:3000 with apiKey in ops and all options" in {
      val u = CoingeckoURI("http://localhost:3000?apiKey=localKey&max=30&throttle=10000&timeout=20000")
      u.apiKey shouldBe "localKey"
      u.max shouldBe 30
      u.throttle shouldBe 10000L
      u.timeout.toMillis shouldBe 20000L
      u.ops should contain allOf ("apiKey" -> "localKey", "max" -> "30", "throttle" -> "10000", "timeout" -> "20000")
      u.getBaseUrl() shouldBe "http://localhost:3000"
    }

    "parse http://localhost:3000 without apiKey (fallback to env)" in {
      val key = sys.env.getOrElse("COINGECKO_API_KEY", "")
      val u = CoingeckoURI("http://localhost:3000")
      u.apiKey shouldBe key
      u.max shouldBe 10
      u.getBaseUrl() shouldBe "http://localhost:3000"
    }

    "parse https://custom-api.example.com style URI" in {
      val u = CoingeckoURI("https://custom-api.example.com")
      u.apiKey shouldBe ""
      u.max shouldBe 10
      u.ops shouldBe Map.empty
      u.getBaseUrl() shouldBe "https://custom-api.example.com"
    }

    "parse https://custom-api.example.com with apiKey in ops" in {
      val u = CoingeckoURI("https://custom-api.example.com?apiKey=customKey&max=50")
      u.apiKey shouldBe "customKey"
      u.max shouldBe 50
      u.ops should contain allOf ("apiKey" -> "customKey", "max" -> "50")
      u.getBaseUrl() shouldBe "https://custom-api.example.com"
    }

    // Tests for default values and edge cases
    "use default throttle value when not specified" in {
      val u = CoingeckoURI("cg://testKey")
      u.throttle shouldBe 30000L
    }

    "use default timeout value when not specified" in {
      val u = CoingeckoURI("cg://testKey")
      u.timeout.toMillis shouldBe 10000L
    }

    "handle empty URI gracefully" in {
      val key = sys.env.getOrElse("COINGECKO_API_KEY", "")
      val u = CoingeckoURI("")
      u.apiKey shouldBe key
      u.max shouldBe 10
      u.ops shouldBe Map.empty
      u.getBaseUrl() shouldBe "https://api.coingecko.com/api/v3"
    }

    "handle malformed URI gracefully" in {
      val key = sys.env.getOrElse("COINGECKO_API_KEY", "")
      val u = CoingeckoURI("invalid://uri:format")
      u.apiKey shouldBe key
      u.max shouldBe 10
      u.getBaseUrl() shouldBe "invalid://uri:format"
    }
  }
}

