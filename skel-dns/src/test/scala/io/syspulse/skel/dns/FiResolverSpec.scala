package io.syspulse.skel.dns

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import scala.util.Failure

class FiResolverSpec extends AnyWordSpec with Matchers {

  val FI_RSP_1 = """
domain.............: lido.fi
status.............: Registered
created............: 5.10.2020 22:24:42
expires............: 5.10.2026 22:24:42
available..........: 5.11.2026 22:24:42
modified...........: 23.9.2025 08:04:31
RegistryLock.......: no

Nameservers

nserver............: nancy.ns.cloudflare.com [OK]
nserver............: jaime.ns.cloudflare.com [OK]

DNSSEC

dnssec.............: no

Holder

holder.............: Private person

Registrar

registrar..........: 1API GmbH
www................: https://www.1api.net

>>> Last update of WHOIS database: 24.6.2026 9:02:05 (EET) <<<

Copyright (c) Finnish Transport and Communications Agency Traficom
"""

  "FiResolver" should {
    "parse FI_RSP_1" in {
      val r = new FiResolver()
      val r1 = r.parseResponse("lido.fi", FI_RSP_1)
      r1 should !== (Failure[DnsInfo](_))
      r1.get should be(
        DnsInfo(
          "lido.fi",
          Some(1601936682000L),
          Some(1758614671000L),
          Some(1791239082000L),
          "",
          Seq("nancy.ns.cloudflare.com", "jaime.ns.cloudflare.com"),
        )
      )
    }

    "parse Finnish date format" in {
      val r = new FiResolver()
      val dates = List(
        "5.10.2020 22:24:42" -> 1601936682000L,
        "23.9.2025 08:04:31" -> 1758614671000L,
        "5.10.2026 22:24:42" -> 1791239082000L,
      )
      dates.foreach { case (dateStr, expected) =>
        withClue(s"Testing date: $dateStr") {
          r.parseFiDate(dateStr) should === (expected)
        }
      }
    }
  }
}
