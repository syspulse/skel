package io.syspulse.skel.util

import org.scalatest.{ Matchers, WordSpec }

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util

class UtilSpec extends WordSpec with Matchers {
  
  "Util" should {

    "sha256 should be 32 bytes" in {
      val bb = Util.SHA256("US")
      bb.size should === (32)
    }

    "convert (US,Country) to the same uuid" in {
      val uuid0 = UUID("aff64e4f-0000-0000-0000-9b202ecbc6d4")
      val uuid1 = Util.uuid("US","country")
      uuid1 should === (uuid0)
      val uuid2 = Util.uuid("US","country")
      uuid2 should === (uuid0)
    }

    "not (UK,Country) equal to (US,Country)" in {
      val uuid1 = Util.uuid("US","country")
      val uuid2 = Util.uuid("UK","country")
      uuid1 should !== (uuid2)
    }

    "convert now timestamp to YYYY-mm string with correct year and month" in {
      val ym = Util.tsToStringYearMonth()
      val lm = LocalDateTime.now
      ym should === (String.format("%d-%02d",lm.getYear,lm.getMonthValue))
    }
    
  }
}