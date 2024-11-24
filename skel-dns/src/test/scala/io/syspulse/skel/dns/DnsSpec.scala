package io.syspulse.skel.dns

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success
import scala.util.Failure
import scala.collection.immutable.ArraySeq

class DnsSpec extends AnyWordSpec with Matchers {
  
  val DST = ZonedDateTime.now.getZone().getRules.isDaylightSavings(ZonedDateTime.now.toInstant)

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

    "resolve google.com" in {                  
      val r1 = DnsUtil.getInfo("google.com")
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (4)
    }

    "resolve across.to" in {                  
      val r1 = DnsUtil.getInfo("across.to")
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (2)
    }
    
    "resolve example.co.uk" in {                  
      val r1 = DnsUtil.getInfo("example.co.uk")
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should !== (Seq())
      r1.get.ns.size should === (2)
    }

    "resolve nhk.uk" in {                  
      val r1 = DnsUtil.getInfo("nhk.uk")
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

    "resolve staking.floki.com as no DNS info, but IP address" in {                  
      val r1 = DnsUtil.getInfo("staking.floki.com")
      r1 should !== (Failure[DnsInfo](_))      
      r1.get.ns should === (Seq())
      r1.get.err should === (Some("not found: staking.floki.com"))
      r1.get.ip should !== ("")
    }
  }
}
