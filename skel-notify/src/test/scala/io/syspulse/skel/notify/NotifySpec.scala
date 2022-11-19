package io.syspulse.skel.notify

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec
import io.syspulse.skel.notify.email.SmtpURI

class NotifySpec extends AnyWordSpec with Matchers {
  
  "SmtpUri" should {
    
    "mail ('smtp://mail.server:587/user/pass/tls')" in {
      val s = SmtpURI("smtp://mail.server:587/user/pass/tls")
      s.host should === ("mail.server")
      s.port should === (587)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (true)
      s.starttls should === (false)
    }

    "mail ('smtp://mail.server:587/user/pass/starttls')" in {
      val s = SmtpURI("smtp://mail.server:587/user/pass/starttls")
      s.host should === ("mail.server")
      s.port should === (587)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (false)
      s.starttls should === (true)
    }

    "mail ('smtp://mail.server:25/user/pass')" in {
      val s = SmtpURI("smtp://mail.server:25/user/pass")
      s.host should === ("mail.server")
      s.port should === (25)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (false)
      s.starttls should === (false)
    }

    "mail ('smtp://mail.server:465/user/pass')" in {
      val s = SmtpURI("smtp://mail.server:465/user/pass")
      s.host should === ("mail.server")
      s.port should === (465)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (true)
      s.starttls should === (false)
    }

    "mail ('smtp://mail.server:587/user/pass')" in {
      val s = SmtpURI("smtp://mail.server:587/user/pass")
      s.host should === ("mail.server")
      s.port should === (587)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (false)
      s.starttls should === (true)
    }

  }
}
