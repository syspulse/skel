package io.syspulse.skel.dns

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext
import scala.util.Failure
import io.syspulse.skel.FutureUtil._

class RdapResolverSpec extends AnyWordSpec with Matchers with DnsTestSupport {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  val RDAP_BOOTSTRAP_1 =
    """
{
  "services": [
    [ [ "limo", "foo" ], [ "https://rdap.identitydigital.services/rdap/" ] ],
    [ [ "uk" ], [ "https://example.uk/rdap/" ] ]
  ]
}
"""

  val LIMO_RSP_1 =
    """
{
  "ldhName": "eth.limo",
  "nameservers": [
    { "ldhName": "ns-814.awsdns-37.net" },
    { "ldhName": "ns-1689.awsdns-19.co.uk" },
    { "ldhName": "ns-48.awsdns-06.com" },
    { "ldhName": "ns-1382.awsdns-44.org" }
  ],
  "events": [
    { "eventAction": "expiration", "eventDate": "2028-06-08T01:12:19.414Z" },
    { "eventAction": "registration", "eventDate": "2021-06-08T01:12:19.414Z" },
    { "eventAction": "last changed", "eventDate": "2026-04-23T14:20:06.772Z" }
  ]
}
"""

  "RdapResolver" should {
    "parse LIMO_RSP_1" in {
      val r = new RdapResolver(Some("https://rdap.identitydigital.services/rdap/"))
      val r1 = r.parseResponse("eth.limo", LIMO_RSP_1)
      r1 should !== (Failure[DnsInfo](_))
      r1.get.ns.size should === (4)
      r1.get.created should === (Some(1623114739414L))
      r1.get.updated should === (Some(1776954006772L))
      r1.get.expire should === (Some(1844039539414L))
    }

    "parse RDAP bootstrap" in {
      val m = RdapResolver.parseBootstrap(RDAP_BOOTSTRAP_1)
      m.get("limo") should === (Some("https://rdap.identitydigital.services/rdap/"))
      m.get("foo") should === (Some("https://rdap.identitydigital.services/rdap/"))
      m.get("uk") should === (Some("https://example.uk/rdap/"))
    }

    "fail for unsupported domain" in {
      val r = new RdapResolver()
      val r1 = syncDns(r.resolve("domain.user"))
      r1.isFailure should === (true)
      val msg = r1.failed.get.getMessage.toLowerCase
      msg should include ("rdap bootstrap failed")
      msg should include ("domain.user")
      msg should not include ("head of empty")
    }

    "fail for incorrect server" in {
      val r = new RdapResolver(Some("https://rdap.identitydigital.services/rdap/bad"))
      val r1 = syncDns(r.resolve("eth.limo"))
      r1.isFailure should === (true)
      val msg = r1.failed.get.getMessage.toLowerCase
      msg should include ("rdap resolve failed")
      msg should include ("eth.limo")
      msg should include ("url=")
      msg should not include ("head of empty")
    }
  }
}
