package io.syspulse.skel.cron

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._
// import io.syspulse.skel.util.Util

class CronSpec extends AnyWordSpec with Matchers {
    
  "CronQuartz" should {

    "NOT schedule events for: '0 0/5 * * * ?'" in {
      var n = 0
      val c = new CronQuartz((elaped:Long) => {
          n = n + 1
          true
        },
        "0 0/5 * * * ?"
      )
      val r = c.start()
      r.getClass should !== (classOf[Failure[_]])
      
      Thread.sleep(1000L)
      c.stop()

      n should === (0)
    }

    "schedule 2 events for: '*/1 * * * * ?'" in {
      var n = 0
      val c = new CronQuartz((elaped:Long) => {
          n = n + 1
          true
        },
        "*/1 * * * * ?"
      )
      val r = c.start()
      r.getClass should !== (classOf[Failure[_]])
      
      Thread.sleep(1200L)
      c.stop()

      n should === (2)
    }
  }    

  "CronFreq" should {
    "parse milliseconds correctly" in {
      CronFreq.parseHuman("100 ms") shouldBe 100L
      CronFreq.parseHuman("1 millisecond") shouldBe 1L
      CronFreq.parseHuman("500 milliseconds") shouldBe 500L
    }

    "parse seconds correctly" in {
      CronFreq.parseHuman("1 sec") shouldBe 1000L
      CronFreq.parseHuman("10 second") shouldBe 10000L
      CronFreq.parseHuman("5 seconds") shouldBe 5000L
    }

    "parse minutes correctly" in {
      CronFreq.parseHuman("1 min") shouldBe 60000L
      CronFreq.parseHuman("2 minute") shouldBe 120000L
      CronFreq.parseHuman("3 minutes") shouldBe 180000L
    }

    "parse hours correctly" in {
      CronFreq.parseHuman("1 hour") shouldBe 3600000L
      CronFreq.parseHuman("2 hours") shouldBe 7200000L
    }

    "parse days correctly" in {
      CronFreq.parseHuman("1 day") shouldBe 86400000L
      CronFreq.parseHuman("2 days") shouldBe 172800000L
    }

    "be case-insensitive" in {
      CronFreq.parseHuman("1 MIN") shouldBe 60000L
      CronFreq.parseHuman("1 Sec") shouldBe 1000L
    }

    "allow spaces between number and unit" in {
      CronFreq.parseHuman("1    min") shouldBe 60000L
      CronFreq.parseHuman("10   seconds") shouldBe 10000L
    }

    "throw IllegalArgumentException for invalid formats" in {
      an [IllegalArgumentException] should be thrownBy CronFreq.parseHuman("1m")
      an [IllegalArgumentException] should be thrownBy CronFreq.parseHuman("minute")
      an [IllegalArgumentException] should be thrownBy CronFreq.parseHuman("1 month")
      an [IllegalArgumentException] should be thrownBy CronFreq.parseHuman("1.5 hours")
    }
    
  }
}

