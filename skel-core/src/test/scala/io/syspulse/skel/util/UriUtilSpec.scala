package io.syspulse.skel.util

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import scala.util.{Success, Failure}
import java.util.Base64
import java.nio.charset.StandardCharsets

class UriUtilSpec extends AnyWordSpec with Matchers {

  def failMsg(uri: String): String =
    UriUtil.uriSanitize(uri) match {
      case Failure(e) => e.getMessage
      case Success(s) => fail(s"expected Failure, got Success($s)")
    }

  "UriUtil.uriSanitize" should {

    "accept empty, emoji, and safe SVG / HTTPS" in {
      val safeSvg = """<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="8"/></svg>"""
      UriUtil.uriSanitize("") shouldBe Success("")
      UriUtil.uriSanitize(None) shouldBe Success(None)
      UriUtil.uriSanitize("🛡️") shouldBe Success("🛡️")
      UriUtil.uriSanitize(safeSvg) shouldBe Success(safeSvg)
      UriUtil.uriSanitize("https://cdn.example.com/icon.png") shouldBe Success("https://cdn.example.com/icon.png")
      UriUtil.uriSanitize("http://example.com/a.svg") shouldBe Success("http://example.com/a.svg")
    }

    "reject SVG foreignObject and script" in {
      failMsg("""<svg><foreignObject><script>alert(1)</script></foreignObject></svg>""") should include ("foreignObject")
      UriUtil.uriSanitize("""<svg><foreign Object></foreignObject></svg>""") shouldBe a [Failure[_]]
      UriUtil.uriSanitize("""<svg><FOREIGN OBJECT></svg>""") shouldBe a [Failure[_]]
      UriUtil.uriSanitize("""<svg>&lt;foreignObject&gt;</svg>""") shouldBe a [Failure[_]]
      failMsg("""<svg><script>alert(1)</script></svg>""") should include ("script")
    }

    "reject HTTP(S) URIs with spaces or onerror" in {
      failMsg("https://cdn.example.com/icon.png onerror=alert(1)") should include ("spaces")
      failMsg("http://example.com/a b.png") should include ("spaces")
      failMsg("""https://cdn.example.com/x.png"onerror="alert(1)""") should include ("onerror")
    }

    "reject javascript: and data:text/html" in {
      failMsg("javascript:alert(1)") should include ("javascript")
      failMsg("data:text/html,<script>alert(1)</script>") should include ("data:text/html")
    }

    "reject data:image/svg+xml with foreignObject" in {
      val inner = """<svg><foreignObject><div>x</div></foreignObject></svg>"""
      UriUtil.uriSanitize("data:image/svg+xml," + inner) shouldBe a [Failure[_]]
      val b64 = Base64.getEncoder.encodeToString(inner.getBytes(StandardCharsets.UTF_8))
      UriUtil.uriSanitize("data:image/svg+xml;base64," + b64) shouldBe a [Failure[_]]
    }
  }
}
