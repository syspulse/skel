package io.syspulse.skel.dns

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Failure

class UkResolverSpec extends AnyWordSpec with Matchers {

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

  "UkResolver" should {
    "parse UK_RSP_1" in {
      val r = new UkResolver()
      val r1 = r.parseResponse("example.co.uk", UK_RSP_1)
      r1 should !== (Failure[DnsInfo](_))
      r1.get should be(
        DnsInfo(
          "example.co.uk",
          Some(848966400000L),
          Some(1668038400000L),
          None,
          "",
          Seq("curt.ns.cloudflare.com", "dee.ns.cloudflare.com"),
        )
      )
    }

    "parse UK date format with LocalDate" in {
      val r = new UkResolver()
      val dates = List(
        "26-Nov-1996" -> 848966400000L,
        "10-Jan-2024" -> 1704844800000L,
        "31-Dec-2023" -> 1703980800000L,
        "01-Mar-2024" -> 1709251200000L,
      )
      dates.foreach { case (dateStr, expected) =>
        withClue(s"Testing date: $dateStr") {
          r.parseUkDate(dateStr) should === (expected)
        }
      }
    }
  }
}
