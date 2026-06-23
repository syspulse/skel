package io.syspulse.skel.dns

// Run with bloop (from repo root): bloop test skel_dns-test

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import java.time._
import io.jvm.uuid._
import scala.util.Failure
import scala.collection.immutable.ArraySeq
import io.syspulse.skel.FutureUtil._
import scala.concurrent.ExecutionContext

class DnsSpec extends AnyWordSpec with Matchers {
  implicit val ec:ExecutionContext = scala.concurrent.ExecutionContext.global

  val RDAP_BOOTSTRAP_1 =
"""
{
  "services": [
    [ [ "limo", "foo" ], [ "https://rdap.identitydigital.services/rdap/" ] ],
    [ [ "uk" ], [ "https://example.uk/rdap/" ] ]
  ]
}
"""

  val TONIC_RSP_1 = """
  <pre>
Domain:               server.to
Created on:           Tue Oct 19 23:12:27 2021
Last edited on:       Tue Sep 19 18:33:25 2023
Expires on:           Sat Oct 19 23:12:27 2024
Primary host add:     None
Primary host name:    raegan.ns.cloudflare.com
Secondary host add:   None
Secondary host name:  west.ns.cloudflare.com

END"""

val UK_RSP_1 = """
                                                                                                                                       
    Domain name:                                                                                                                       
        example.co.uk                                                                                                                  
                                                                                                                                       
    Registrant:                                                                                                                        
        Nominet UK                                                                                                                     
                                                                                                                                       
    Registrant type:                                         
        UK Limited Company, (Company number: 3203859)

    Registrant's address:
        Minerva House
        Edmund Halley Road
        Oxford Science Park
        Oxford
        Oxon
        OX4 4DQ
        United Kingdom

    Data validation:
        Nominet was able to match the registrant's name and address against a 3rd party data source on 26-Oct-2018

    Registrar:
        No registrar listed.  This domain is registered directly with Nominet.

    Relevant dates:
        Registered on: 26-Nov-1996
        Last updated:  10-Nov-2022

    Registration status:
        No registration status listed.

    Name servers:
        curt.ns.cloudflare.com
        dee.ns.cloudflare.com

    DNSSEC:
        Signed

    WHOIS lookup made at 12:41:41 23-Nov-2024

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

  "DnsUtil" should {
  
    "parse TonicResolver for TONIC_RSP_1" in {
      val r = new TonicResolver()
      
      val r1 = r.parseResponse("server.to",TONIC_RSP_1)      
      r1 should !== (Failure[DnsInfo](_))
      r1.get should be (DnsInfo("server.to",Some(1634685147000L),Some(1695148405000L),Some(1729379547000L),"",Seq("raegan.ns.cloudflare.com", "west.ns.cloudflare.com"))) //(DnsInfo("across.to",_,_,_,_,_))
    }

    "parse UkResolver for UK_RSP_1" in {
      val r = new UkResolver()
      
      val r1 = r.parseResponse("example.co.uk",UK_RSP_1)
      r1 should !== (Failure[DnsInfo](_))
      r1.get should be (DnsInfo("example.co.uk",Some(848966400000L),Some(1668038400000L),None,"",Seq("curt.ns.cloudflare.com", "dee.ns.cloudflare.com")))
    }

    "parse RdapResolver " in {
      val r = new RdapResolver(Some("https://rdap.identitydigital.services/rdap/"))
      val r1 = r.parseResponse("eth.limo",LIMO_RSP_1)
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

    "fail RDAP for unsupported domain" in {
      val r = new RdapResolver()
      val r1 = sync(r.resolve("domain.user"))
      r1.isFailure should === (true)
      val msg = r1.failed.get.getMessage.toLowerCase
      msg should include ("rdap bootstrap failed")
      msg should include ("domain.user")
      msg should not include ("head of empty")
    }

    "whois should not throw head-of-empty for unknown zone" in {
      val r = new WhoisResolver()
      val r1 = sync(r.resolve("domain.user"))
      // may succeed or fail depending on network/registry, but must not crash with head-of-empty
      val msg = r1.failed.toOption.map(_.getMessage.toLowerCase).getOrElse("")
      msg should not include ("head of empty")
    }

    "fail RDAP for incorrect server" in {
      // Force a fast, comprehensible HTTP failure (404) from a real host.
      val r = new RdapResolver(Some("https://rdap.identitydigital.services/rdap/bad"))
      val r1 = sync(r.resolve("eth.limo"))
      r1.isFailure should === (true)
      val msg = r1.failed.get.getMessage.toLowerCase
      msg should include ("rdap resolve failed")
      msg should include ("eth.limo")
      msg should include ("url=")
      msg should not include ("head of empty")
    }

    "resolve google.com" in {                  
      val r1 = sync(DnsUtil.getInfo("google.com"))
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (4)
    }

    "resolve across.to" in {                  
      val r1 = sync(DnsUtil.getInfo("across.to"))
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (2)
    }
    
    "resolve example.co.uk" in {                  
      val r1 = sync(DnsUtil.getInfo("example.co.uk"))
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (2)
    }

    "resolve nhk.uk" in {                  
      val r1 = sync(DnsUtil.getInfo("nhk.uk"))
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (2)
    }

    "parse UK date format with LocalDate" in {
      val r = new UkResolver()
      
      val dates = List(
        "26-Nov-1996" -> 848966400000L,  // 1996-11-26 00:00:00 UTC
        "10-Jan-2024" -> 1704844800000L,  // 2024-01-10 00:00:00 UTC
        "31-Dec-2023" -> 1703980800000L,  // 2023-12-31 00:00:00 UTC
        "01-Mar-2024" -> 1709251200000L   // 2024-03-01 00:00:00 UTC
      )

      dates.foreach { case (dateStr, expected) =>
        withClue(s"Testing date: $dateStr") {
          r.parseUkDate(dateStr) should === (expected)
        }
      }
    }

    "resolve staking.floki.com as no NS info, but IP address" in {                  
      val r1 = sync(DnsUtil.getInfo("staking.floki.com"))
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should === (Seq())
      r1.get.err should === (Some("not found: staking.floki.com"))
      r1.get.ip should !== ("")
    }

    "resolve safe.global" in {                  
      val r1 = sync(DnsUtil.getInfo("safe.global"))
      info(s"safe: ${r1}")
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (4)
      r1.get.ip should !== ("")
    }
  }
}
