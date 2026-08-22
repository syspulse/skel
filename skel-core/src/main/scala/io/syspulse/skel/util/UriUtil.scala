package io.syspulse.skel.util

import scala.util.{Try, Success, Failure}
import java.nio.charset.StandardCharsets
import java.net.URLDecoder
import java.util.Base64

/**
 * Sanitize a URI / SVG markup against XSS injection (e.g. icon fields).
 *
 * SVG: reject `foreignObject` (can embed HTML/`<script>`), `<script>`, and other HTML hosts.
 * HTTP/HTTPS: reject spaces and `onerror` (attribute-injection / XSS).
 */
object UriUtil {

  def uriSanitize(uri: String): Try[String] = {
    if (uri == null) Failure(new IllegalArgumentException("uri: null"))
    else {
      val raw = uri.trim
      if (raw.isEmpty) Success(raw)
      else {
        val lower = raw.toLowerCase
        val payload = svgPayload(raw)
        val compact = compactForCheck(payload)
        if (isHttpUri(lower)) httpUriCheck(raw)
        else if (isSvgUri(lower, compact, payload)) svgUriCheck(raw, compact)
        else if (lower.startsWith("javascript:") || compact.contains("javascript:"))
          Failure(new IllegalArgumentException("uri: javascript URI is not allowed"))
        else if (lower.startsWith("data:text/html") || compact.contains("data:text/html"))
          Failure(new IllegalArgumentException("uri: data:text/html is not allowed"))
        else Success(raw)
      }
    }
  }

  def uriSanitize(uri: Option[String]): Try[Option[String]] = uri match {
    case None    => Success(None)
    case Some(s) => uriSanitize(s).map(Some(_))
  }

  private def isHttpUri(lower: String): Boolean =
    lower.startsWith("http://") || lower.startsWith("https://")

  private def isSvgUri(lower: String, compact: String, payload: String): Boolean = {
    val p = payload.trim.toLowerCase
    lower.startsWith("data:image/svg") ||
      lower.contains("<svg") || compact.contains("<svg") ||
      p.startsWith("<svg") || p.startsWith("<?xml")
  }

  private def httpUriCheck(raw: String): Try[String] = {
    if (raw.exists(_.isWhitespace))
      Failure(new IllegalArgumentException("uri: HTTP(S) URI must not contain spaces"))
    else if (raw.toLowerCase.contains("onerror"))
      Failure(new IllegalArgumentException("uri: HTTP(S) URI must not contain onerror"))
    else Success(raw)
  }

  private def svgUriCheck(raw: String, compact: String): Try[String] = {
    def bad(reason: String) = Failure(new IllegalArgumentException(s"uri: ${reason}"))
    if (compact.contains("foreignobject")) bad("SVG foreignObject is not allowed")
    else if (compact.contains("<script") || compact.contains("</script")) bad("SVG script is not allowed")
    else if (compact.contains("<iframe")) bad("SVG iframe is not allowed")
    else if (compact.contains("<object") || compact.contains("<embed")) bad("SVG object/embed is not allowed")
    else if (compact.contains("javascript:")) bad("SVG javascript URI is not allowed")
    else if (compact.contains("onerror")) bad("SVG onerror is not allowed")
    else Success(raw)
  }

  private val HEX_ENT = "(?i)&#x([0-9a-f]+);".r
  private val DEC_ENT = "&#([0-9]+);".r

  private def decodeHtmlEntities(s: String): String = {
    val named = s
      .replace("&lt;", "<").replace("&LT;", "<")
      .replace("&gt;", ">").replace("&GT;", ">")
      .replace("&quot;", "\"").replace("&apos;", "'")
      .replace("&amp;", "&")
    val hex = HEX_ENT.replaceAllIn(named, m =>
      Try(Integer.parseInt(m.group(1), 16)).map(c => htmlCp(c, m.group(0))).getOrElse(m.group(0)))
    DEC_ENT.replaceAllIn(hex, m =>
      Try(m.group(1).toInt).map(c => htmlCp(c, m.group(0))).getOrElse(m.group(0)))
  }

  private def htmlCp(code: Int, fallback: String): String =
    if (code >= 0 && code <= 0x10FFFF) new String(Character.toChars(code)) else fallback

  private def decodeUrl(s: String): String =
    Try(URLDecoder.decode(s, StandardCharsets.UTF_8.name())).getOrElse(s)

  private def svgPayload(s: String): String = {
    val lower = s.toLowerCase
    if (!lower.startsWith("data:image/svg")) return s
    val comma = s.indexOf(',')
    if (comma < 0) return s
    val body = s.substring(comma + 1)
    if (lower.contains(";base64"))
      Try(new String(Base64.getDecoder.decode(body.filterNot(_.isWhitespace)), StandardCharsets.UTF_8)).getOrElse(s)
    else decodeUrl(body)
  }

  private def compactForCheck(s: String): String =
    decodeHtmlEntities(decodeUrl(s)).toLowerCase.filterNot(c => c.isWhitespace || c == '\u0000')
}
