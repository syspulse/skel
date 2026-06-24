package io.syspulse.skel.dns

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Failure

class TonicResolverSpec extends AnyWordSpec with Matchers {

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

  "TonicResolver" should {
    "parse TONIC_RSP_1" in {
      val r = new TonicResolver()
      val r1 = r.parseResponse("server.to", TONIC_RSP_1)
      r1 should !== (Failure[DnsInfo](_))
      r1.get should be(
        DnsInfo(
          "server.to",
          Some(1634685147000L),
          Some(1695148405000L),
          Some(1729379547000L),
          "",
          Seq("raegan.ns.cloudflare.com", "west.ns.cloudflare.com"),
        )
      )
    }
  }
}
