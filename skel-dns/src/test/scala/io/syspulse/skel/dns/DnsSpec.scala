package io.syspulse.skel.dns

// Run with bloop (from repo root): bloop test skel_dns-test

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.concurrent.ExecutionContext

class DnsSpec extends AnyWordSpec with Matchers with DnsTestSupport {
  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.global

  "DnsUtil" should {
    "getResolver select resolver from scheme prefix" in {
      val (r1, d1) = DnsUtil.getResolver("whois://google.com")
      r1 shouldBe a[WhoisResolver]
      d1 shouldBe "google.com"

      val (r2, d2) = DnsUtil.getResolver("rdap://eth.limo")
      r2 shouldBe a[RdapResolver]
      d2 shouldBe "eth.limo"

      val (r3, d3) = DnsUtil.getResolver("https://google.com")
      r3 shouldBe a[AutoResolver]
      d3 shouldBe "google.com"

      val (r4, d4) = DnsUtil.getResolver("google.com")
      r4 shouldBe a[AutoResolver]
      d4 shouldBe "google.com"
    }

    "resolve google.com with ip and nameservers" in {
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("google.com")), "google.com", minNs = 4)
    }

    "resolve across.to with ip and nameservers" in {
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("across.to")), "across.to", minNs = 2)
    }

    "resolve example.co.uk with ip and nameservers" in {
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("example.co.uk")), "example.co.uk", minNs = 2)
    }

    "resolve nhk.uk with ip and nameservers" in {
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("nhk.uk")), "nhk.uk", minNs = 2)
    }

    "resolve staking.floki.com as no NS info, but IP address" in {
      assertSuccess(syncDns(DnsUtil.getInfo("staking.floki.com")), "staking.floki.com") { d =>
        d.ns should === (Seq())
        d.ip should not be empty
      }
    }

    "resolve whois://google.com with ip and nameservers" in {
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("whois://google.com")), "whois://google.com", minNs = 4)
    }

    "resolve rdap://eth.limo with ip and nameservers" in {
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("rdap://eth.limo")), "rdap://eth.limo", minNs = 1)
    }

    "resolve https://google.com with ip and nameservers" in {
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("https://google.com")), "https://google.com", minNs = 4)
    }

    "resolve lido.fi via AutoResolver with nameservers" in {
      val (r, d) = DnsUtil.getResolver("lido.fi")
      r shouldBe a[AutoResolver]
      d shouldBe "lido.fi"
      assertResolvedWithIpAndNs(syncDns(DnsUtil.getInfo("lido.fi")), "lido.fi", minNs = 2)
    }
  }
}
