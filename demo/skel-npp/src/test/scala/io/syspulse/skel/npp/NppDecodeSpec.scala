package io.syspulse.skel.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util

class NppDecodeSpec extends WordSpec with Matchers with FlowTestable {
  
  "NppDecodeSpec" should {

    "decode data/time to ZonedDateTime" in {
      val date = "14.08.2021"
      val time = "14:00"

      val zdt = NppDecode.decodeTsToZone(date,time)
      val offset = ZoneId.of("Europe/Kiev").getRules.getOffset(Instant.now).toString
      zdt.toString should === (s"2021-08-14T14:00${offset}[Europe/Kiev]")
    }

    "decode data/time to timestamp" in {
      val date = "14.08.2021"
      val time = "14:00"

      val ts = NppDecode.decodeTs(date,time)
      ts should === (1628938800000L)
    }

    "decode Latitude" in {
      val lat1 = "N051.391494"

      val lat = NppDecode.decodeLat(lat1)
      lat should  === (51.391494)
    }

    "decode Longitude" in {
      val lon1 = "E030.101595"

      val lon = NppDecode.decodeLon(lon1)
      lon should  === (30.101595)
    }

    "decode dose" in {
      val dos1 = "3540 nSv/h"

      val d = NppDecode.decodeDose(dos1)
      d should  === (3540.0)
    }
  }
}
