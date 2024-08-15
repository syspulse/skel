package io.syspulse.skel.dns

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success
import io.syspulse.dns.TonicResolver
import io.syspulse.dns.DnsInfo
import scala.util.Failure
import scala.collection.immutable.ArraySeq

class DnsSpec extends AnyWordSpec with Matchers {
  
  val DST = ZonedDateTime.now.getZone().getRules.isDaylightSavings(ZonedDateTime.now.toInstant)

  val TONIC_RSP_1 = """
  <pre>
Domain:               across.to
Created on:           Tue Oct 19 23:12:27 2021
Last edited on:       Tue Sep 19 18:33:25 2023
Expires on:           Sat Oct 19 23:12:27 2024
Primary host add:     None
Primary host name:    raegan.ns.cloudflare.com
Secondary host add:   None
Secondary host name:  west.ns.cloudflare.com

END"""

  "DnsUtil" should {
  
    "parse TonicResolver for TONIC_RSP_1" in {
      val r = new TonicResolver()
      
      val r1 = r.parseResponse("across.to",TONIC_RSP_1)      
      r1 should !== (Failure[DnsInfo](_))
      r1.get should be (DnsInfo("across.to",Some(1634685147000L),Some(1695148405000L),Some(1729379547000L),"",Seq("raegan.ns.cloudflare.com", "west.ns.cloudflare.com"))) //(DnsInfo("across.to",_,_,_,_,_))
    }


  }
}
