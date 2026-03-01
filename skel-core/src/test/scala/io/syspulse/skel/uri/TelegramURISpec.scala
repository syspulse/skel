package io.syspulse.skel.uri

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class TelegramURISpec extends AnyWordSpec with Matchers {

  "TelegramURI" should {

    "parse bot_token@channels" in {
      val u = TelegramURI("telegram://mytoken@channel1,channel2")
      u.botToken shouldBe "mytoken"
      u.channels shouldBe Seq("channel1", "channel2")
      u.freq shouldBe 3000L
      u.timeout shouldBe 30000L
      u.buffer shouldBe 1000
      u.max shouldBe 100
      u.allowedUpdates shouldBe Seq("message", "channel_post")
    }

    "parse bot_token@single_channel" in {
      val u = TelegramURI("telegram://mytoken@-1003611976348")
      u.botToken shouldBe "mytoken"
      u.channels shouldBe Seq("-1003611976348")
    }

    "parse with query params freq, timeout, buffer, max" in {
      val u = TelegramURI("telegram://mytoken@ch1,ch2?freq=5000&timeout=15000&buffer=2000&max=50")
      u.botToken shouldBe "mytoken"
      u.channels shouldBe Seq("ch1", "ch2")
      u.freq shouldBe 5000L
      u.timeout shouldBe 15000L
      u.buffer shouldBe 2000
      u.max shouldBe 50
      u.ops should contain allOf ("freq" -> "5000", "timeout" -> "15000", "buffer" -> "2000", "max" -> "50")
    }

    "parse allowed_updates from query" in {
      val u = TelegramURI("telegram://mytoken@ch1?allowed_updates=message,edited_message")
      u.botToken shouldBe "mytoken"
      u.channels shouldBe Seq("ch1")
      u.allowedUpdates shouldBe Seq("message", "edited_message")
    }

    "parse channels only (no @) - bot token from env" in {
      val u = TelegramURI("telegram://-1003611976348,group2")
      u.channels shouldBe Seq("-1003611976348", "group2")
      u.botToken shouldBe sys.env.getOrElse("TELEGRAM_BOT_TOKEN", "")
    }

    "preserve ops map" in {
      val u = TelegramURI("telegram://tok@ch?freq=1&foo=bar")
      u.ops should contain ("foo" -> "bar")
      u.ops should contain ("freq" -> "1")
    }

    "handle empty path - empty channels and token from env" in {
      val u = TelegramURI("telegram://")
      u.botToken shouldBe sys.env.getOrElse("TELEGRAM_BOT_TOKEN", "")
      u.channels shouldBe Seq.empty
      u.freq shouldBe 3000L
      u.timeout shouldBe 30000L
    }

    "use default freq and timeout when not in query" in {
      val u = TelegramURI("telegram://tok@ch")
      u.freq shouldBe TelegramURI("telegram://x@y").DEF_FREQ
      u.timeout shouldBe TelegramURI("telegram://x@y").DEF_TIMEOUT
    }
  }
}
