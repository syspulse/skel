package io.syspulse.skel.scrap.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util

class NppSpec extends WordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

  implicit val doubleEquality = TolerantNumerics.tolerantDoubleEquality(0.0001)

  val sk1 = "0x00d0f37e94ba4d144291b745212bcb49fff3a6c06f280371faa6dc07640d631ecc"
  val pk1 = "0x6a9218674affe7ffcca2baccc261260e3f2f30166ac1f481d426898236c03d8993b526760c432c643d8be796ff5e3d096152582a4317f3370b8783d2c47274f8"

  "NppSpec" should {

    "decode data/time to ZonedDateTime" in {
      val date = "14.08.2021"
      val time = "14:00"

      val zdt = new NppScrap().decodeTsToZone(date,time)
      val offset = ZoneId.of("Europe/Kiev").getRules.getOffset(Instant.now).toString
      zdt.toString should === (s"2021-08-14T14:00${offset}[Europe/Kiev]")
    }

    "decode data/time to timestamp" in {
      val date = "14.08.2021"
      val time = "14:00"

      val ts = new NppScrap().decodeTs(date,time)
      ts should === (1628938800000L)
    }

    "decode Latitude" in {
      val lat1 = "N051.391494"

      val lat = new NppScrap().decodeLat(lat1)
      lat should  === (51.391494)
    }

    "decode Longitude" in {
      val lon1 = "E030.101595"

      val lon = new NppScrap().decodeLon(lon1)
      lon should  === (30.101595)
    }

    "decode dose" in {
      val dos1 = "3540 nSv/h"

      val d = new NppScrap().decodeDose(dos1)
      d should  === (3540.0)
    }

    "load data for NPP" in {
      val npp = new NppScrap()
      val r = npp.scrap()
      //info(r.toString)
      r.size should  === (66)
      r.head.getClass should !== (Radiation.getClass())

      // check unique areas
      r.groupBy(_.area).size should === (66)
    }
  }
}
