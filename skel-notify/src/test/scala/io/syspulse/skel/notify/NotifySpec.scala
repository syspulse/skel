package io.syspulse.skel.notify

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import akka.http.javadsl.model.HttpMethod
import akka.http.scaladsl.model.HttpMethods
import akka.http.scaladsl.model.headers.RawHeader

import io.syspulse.skel.notify._
import io.syspulse.skel.notify.email._
import io.syspulse.skel.notify.http._

class NotifySpec extends AnyWordSpec with Matchers {
  
  "SmtpUri" should {
    
    "parse mail ('smtp://mail.server:587/user/pass/tls')" in {
      val s = SmtpURI("smtp://mail.server:587/user/pass/tls")
      s.host should === ("mail.server")
      s.port should === (587)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (true)
      s.starttls should === (false)
    }

    "parse mail ('smtp://mail.server:587/user/pass/starttls')" in {
      val s = SmtpURI("smtp://mail.server:587/user/pass/starttls")
      s.host should === ("mail.server")
      s.port should === (587)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (false)
      s.starttls should === (true)
    }

    "parse mail ('smtp://mail.server:25/user/pass')" in {
      val s = SmtpURI("smtp://mail.server:25/user/pass")
      s.host should === ("mail.server")
      s.port should === (25)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (false)
      s.starttls should === (false)
    }

    "parse mail ('smtp://mail.server:465/user/pass')" in {
      val s = SmtpURI("smtp://mail.server:465/user/pass")
      s.host should === ("mail.server")
      s.port should === (465)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (true)
      s.starttls should === (false)
    }

    "parse mail ('smtp://mail.server:587/user/pass')" in {
      val s = SmtpURI("smtp://mail.server:587/user/pass")
      s.host should === ("mail.server")
      s.port should === (587)
      s.user should === ("user")
      s.pass should === ("pass")
      s.tls should === (false)
      s.starttls should === (true)
    }

  }

  "HttpURI" should {

    "parse http ('http://localhost:8300/')" in {
      val n = new NotifyHttp("http://localhost:8300")
      n.request.uri should === ("http://localhost:8300")
      n.request.verb should === (HttpMethods.GET)
    }

    "parse http ('http://localhost:8300/{msg}')" in {
      val n = new NotifyHttp("http://localhost:8300/{msg}")
      n.request.uri should === ("http://localhost:8300/{msg}")
    }

    "parse http ('http://localhost:8300/{msg}') with ('subj1,msg1')" in {
      val n = new NotifyHttp("http://localhost:8300/{msg}")      
      val r = n.request.withUri("subj1","msg1")
      r.uri should === ("http://localhost:8300/msg1")
    }

    "http ('http://POST@localhost:8300/{msg}')" in {
      val n = new NotifyHttp("http://POST@localhost:8300/{msg}")
      n.request.uri should === ("http://localhost:8300/{msg}")
      n.request.verb should === (HttpMethods.POST)
    }

    "parse http ('https://POST@123456789@localhost:8300/{msg}')" in {
      val n = new NotifyHttp("https://POST@123456789@localhost:8300/{msg}")
      info(s"${n.request}")
      n.request.uri should === ("https://localhost:8300/{msg}")
      n.request.verb should === (HttpMethods.POST)
      n.request.getHeaders should === (Seq(RawHeader("Authorization","Bearer 123456789")))
    }
  }

  "NotifyUri" should {
    implicit val config:Config = Config()

    "parse http 'https://POST@123456789@localhost:8300/' to NotifyHttp" in {
      val n = NotifyUri("https://POST@123456789@localhost:8300/")
      //info(s"${n}")
      n.isInstanceOf[NotifyHttp] should === (true)
    }

    "parse http 'event://https://POST@123456789@localhost:8300/' to NotifyEmbed(NotifyHttp)" in {
      val n = NotifyUri("event://https://POST@123456789@localhost:8300/")
      info(s"${n}")
      n.isInstanceOf[NotifyEmbed[_]] should === (true)
      n.asInstanceOf[NotifyEmbed[_]].getEmbed.isInstanceOf[NotifyHttp] should === (true)

      n.asInstanceOf[NotifyEmbed[_]].getEmbed.asInstanceOf[NotifyHttp].request.uri should === ("https://localhost:8300/")
      n.asInstanceOf[NotifyEmbed[_]].getEmbed.asInstanceOf[NotifyHttp].request.verb.value should === (HttpMethods.POST.value)
    }
  }
}
