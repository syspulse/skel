package io.syspulse.skel.coingecko

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
    }

    "fallback to env when missing apiKey" in {
      val key = sys.env.getOrElse("COINGECKO_API_KEY", "")
      val u = CoingeckoURI("coingecko://")
      u.apiKey shouldBe key
      u.max shouldBe 10
    }

    "preserve extra ops" in {
      val u = CoingeckoURI("coingecko://k?foo=bar&max=7&x=1")
      u.apiKey shouldBe "k"
      u.max shouldBe 7
      u.ops should contain allOf ("foo" -> "bar", "x" -> "1", "max" -> "7")
    }
  }
}

