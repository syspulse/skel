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

  // https://rdap.identitydigital.services/rdap/domain/compound.finance
  val COMPOUND_FINANCE_RSP =
    """
{
  "ldhName": "compound.finance",
  "nameservers": [
    {
      "ldhName": "karl.ns.cloudflare.com",
      "objectClassName": "nameserver",
      "status": [ "associated" ]
    },
    {
      "ldhName": "sharon.ns.cloudflare.com",
      "objectClassName": "nameserver",
      "status": [ "associated" ]
    }
  ],
  "events": [
    { "eventAction": "expiration", "eventDate": "2027-09-25T06:30:37.317Z" },
    { "eventAction": "registration", "eventDate": "2017-09-25T06:30:37.317Z" },
    { "eventAction": "last changed", "eventDate": "2024-12-16T19:28:16.003Z" }
  ]
}
"""

  // https://rdap.fi/rdap/rdap/domain/cow.fi
  val COW_FI_RSP =
    """
{
  "ldhName": "cow.fi",
  "nameservers": [
    { "ldhName": "ns-90.awsdns-11.com [OK]" },
    { "ldhName": "ns-1002.awsdns-61.net [OK]" },
    { "ldhName": "ns-1378.awsdns-44.org [OK]" },
    { "ldhName": "ns-1917.awsdns-47.co.uk [OK]" }
  ],
  "events": [
    { "eventAction": "registration", "eventDate": "2019-04-22T17:16:10+03:00" }
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

    "parse compound.finance nameservers with nested status arrays" in {
      val r = new RdapResolver(Some("https://rdap.identitydigital.services/rdap/"))
      val r1 = r.parseResponse("compound.finance", COMPOUND_FINANCE_RSP)
      r1 should !== (Failure[DnsInfo](_))
      r1.get.ns should === (Seq("karl.ns.cloudflare.com", "sharon.ns.cloudflare.com"))
      r1.get.created should === (Some(1506321037317L))
      r1.get.updated should === (Some(1734377296003L))
      r1.get.expire should === (Some(1821853837317L))
    }

    "parse cow.fi nameservers" in {
      val r = new RdapResolver(Some("https://rdap.fi/rdap/rdap/"))
      val r1 = r.parseResponse("cow.fi", COW_FI_RSP)
      r1 should !== (Failure[DnsInfo](_))
      r1.get.ns should === (Seq(
        "ns-90.awsdns-11.com",
        "ns-1002.awsdns-61.net",
        "ns-1378.awsdns-44.org",
        "ns-1917.awsdns-47.co.uk"
      ))
      r1.get.created should === (Some(1555942570000L))
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
