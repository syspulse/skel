package io.syspulse.skel.cron

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._
import io.syspulse.skel.util.TimeUtil
// import io.syspulse.skel.util.Util

class CronSpec extends AnyWordSpec with Matchers {
    
  "CronQuartz" should {

    "NOT schedule events for: '0 0/5 * * * ?'" in {
      @volatile var n = 0
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
      @volatile var n = 0
      val c = new CronQuartz((elaped:Long) => {
          n = n + 1
          true
        },
        "*/1 * * * * ?"
      )
      val r = c.start()
      r.getClass should !== (classOf[Failure[_]])
      
      Thread.sleep(1100L)
      c.stop()

      n should === (2)
    }

    "interval '*/1 * * * * ?' == 1000" in {
      val i = CronQuartz.toMillis("*/1 * * * * ?")
      i should === (1000L)
    }

    "interval '0 */2 * ? * *' == 120000" in {
      val i = CronQuartz.toMillis("0 */2 * ? * *")
      i should === (120000L)
    }
  }
  
  "CronFreq" should {

    "parse only milliseconds correctly" in {
      TimeUtil.humanToMillis("100") shouldBe 100L
      TimeUtil.humanToMillis("1") shouldBe 1L
      TimeUtil.humanToMillis("500") shouldBe 500L
    }

    "parse milliseconds correctly" in {
      TimeUtil.humanToMillis("100 ms") shouldBe 100L
      TimeUtil.humanToMillis("1 millisecond") shouldBe 1L
      TimeUtil.humanToMillis("500 milliseconds") shouldBe 500L
    }

    "parse seconds correctly" in {
      TimeUtil.humanToMillis("1 sec") shouldBe 1000L
      TimeUtil.humanToMillis("10 second") shouldBe 10000L
      TimeUtil.humanToMillis("5 seconds") shouldBe 5000L
    }

    "parse minutes correctly" in {
      TimeUtil.humanToMillis("1 min") shouldBe 60000L
      TimeUtil.humanToMillis("2 minute") shouldBe 120000L
      TimeUtil.humanToMillis("3 minutes") shouldBe 180000L
    }

    "parse hours correctly" in {
      TimeUtil.humanToMillis("1 hour") shouldBe 3600000L
      TimeUtil.humanToMillis("2 hours") shouldBe 7200000L
    }

    "parse days correctly" in {
      TimeUtil.humanToMillis("1 day") shouldBe 86400000L
      TimeUtil.humanToMillis("2 days") shouldBe 172800000L
    }

    "be case-insensitive" in {
      TimeUtil.humanToMillis("1 MIN") shouldBe 60000L
      TimeUtil.humanToMillis("1 Sec") shouldBe 1000L
    }

    "allow spaces between number and unit" in {
      TimeUtil.humanToMillis("1    min") shouldBe 60000L
      TimeUtil.humanToMillis("10   seconds") shouldBe 10000L
    }

    "throw IllegalArgumentException for invalid formats" in {
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("-1m")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("minute")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1 mAnth")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1.5 Zours")
    }
    
  }

  "Cron" should {

    "limit 100msec to 1sec" in {
      @volatile var n = 0
      val c = Cron((elaped:Long) => {
          n = n + 1
          true
        },
        "100msec",
        rateLimit = Some(1000L)
      )
      val r = c.start()
      Thread.sleep(1250L)
      c.stop()

      // should be 2 since it fires extra one immediately
      n should === (2)      
    }

    "limit 1sec to 2sec" in {
      @volatile var n = 0
      val c = Cron((elaped:Long) => {
          n = n + 1
          true
        },
        "*/1 * * * * ?",
        rateLimit = Some(2000L)
      )
      val r = c.start()
      
      Thread.sleep(1250L)
      n should === (0)
      
      Thread.sleep(1250L)      
      c.stop()      
      n should === (1)
    }
  }
}

