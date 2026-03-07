package io.syspulse.skel.uri

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class TelURISpec extends AnyWordSpec with Matchers {

  "TelURI" should {

    "parse tel://phone (digits only)" in {
      val u = TelURI("tel://1234567890")
      u.phone shouldBe Some("1234567890")
      u.sessionPath shouldBe "1234567890.session"
      u.freq shouldBe 3000L
      u.timeout shouldBe 30000L
      u.buffer shouldBe 1000
      u.max shouldBe 100
    }

    "parse tel://phone with plus" in {
      val u = TelURI("tel://+1234567890")
      u.phone shouldBe Some("+1234567890")
      u.sessionPath shouldBe "1234567890.session"
    }

    "parse tel://phone?session=path" in {
      val u = TelURI("tel://1234567890?session=/tmp/my.session")
      u.phone shouldBe Some("1234567890")
      u.sessionPath shouldBe "/tmp/my.session"
    }

    "parse tel:// with api_id, api_hash in query (phone from env only)" in {
      val u = TelURI("tel://?api_id=12345&api_hash=abcdef")
      u.apiId shouldBe Some(12345)
      u.apiHash shouldBe Some("abcdef")
      u.phone shouldBe sys.env.get("TELEGRAM_PHONE")
    }

    "parse tel://phone with api_id and api_hash in query" in {
      val u = TelURI("tel://1234567890?api_id=1&api_hash=hash")
      u.phone shouldBe Some("1234567890")
      u.apiId shouldBe Some(1)
      u.apiHash shouldBe Some("hash")
    }

    "use default session path from phone when session not in URI" in {
      val u = TelURI("tel://+15551234567")
      u.sessionPath shouldBe "15551234567.session"
    }

    "phoneForSessionFile strips plus and non-digits" in {
      val u = TelURI("tel://123")
      u.phoneForSessionFile("+1234567890") shouldBe "1234567890"
      u.phoneForSessionFile("1234567890") shouldBe "1234567890"
    }

    "parse query params freq, timeout, buffer, max" in {
      val u = TelURI("tel://1234567890?freq=5000&timeout=20000&buffer=500&max=25")
      u.phone shouldBe Some("1234567890")
      u.freq shouldBe 5000L
      u.timeout shouldBe 20000L
      u.buffer shouldBe 500
      u.max shouldBe 25
      u.ops should contain allOf ("freq" -> "5000", "timeout" -> "20000", "buffer" -> "500", "max" -> "25")
    }

    "parse allowed_updates from query" in {
      val u = TelURI("tel://1234567890?allowed_updates=message,channel_post")
      u.allowedUpdates shouldBe Seq("message", "channel_post")
    }

    "preserve ops map" in {
      val u = TelURI("tel://1234567890?api_id=1&foo=bar")
      u.ops should contain ("foo" -> "bar")
      u.ops should contain ("api_id" -> "1")
    }

    "handle empty path - phone from env" in {
      val u = TelURI("tel://")
      u.phone shouldBe sys.env.get("TELEGRAM_PHONE")
      u.freq shouldBe 3000L
      u.apiId shouldBe sys.env.get("TELEGRAM_API_ID").map(_.toInt)
      u.apiHash shouldBe sys.env.get("TELEGRAM_API_HASH")
    }

    "use default freq and timeout when not in query" in {
      val u = TelURI("tel://123")
      u.freq shouldBe TelURI("tel://123").DEF_FREQ
      u.timeout shouldBe TelURI("tel://123").DEF_TIMEOUT
    }

    "ignore non-phone path (phone from env only)" in {
      val u = TelURI("tel://not-a-phone")
      u.phone shouldBe sys.env.get("TELEGRAM_PHONE")
    }

    "use default auth_timeout_sec and loop_interval_ms when not in query" in {
      val u = TelURI("tel://123")
      u.authTimeoutSec shouldBe 300L
      u.loopIntervalMs shouldBe 1000L
    }

    "parse auth_timeout_sec and loop_interval_ms from query" in {
      val u = TelURI("tel://123?auth_timeout_sec=120&loop_interval_ms=500")
      u.authTimeoutSec shouldBe 120L
      u.loopIntervalMs shouldBe 500L
    }
  }
}
