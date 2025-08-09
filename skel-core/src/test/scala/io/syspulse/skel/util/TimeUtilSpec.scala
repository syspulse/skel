package io.syspulse.skel.util

import scala.util.{Try,Success,Failure}

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._
import java.time._
import io.syspulse.skel.util.Util


class TimeUtilSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

  "TimeUtilSpec" should {

    "convert 'Today' to current day" in {
      val d1 = TimeUtil.wordToDate("Today")
      d1.get.getDayOfWeek should === (ZonedDateTime.now.getDayOfWeek)
    }

    "convert 'YESterday' to prev day" in {
      val d1 = TimeUtil.wordToDate("YESterday")
      d1.get.getDayOfWeek should === (ZonedDateTime.now.minusDays(1).getDayOfWeek)
    }

    "convert '3 DAYS ago' to prev 3-rd day" in {
      val d1 = TimeUtil.wordToDate("3 DAYS ago")
      d1.get.getDayOfWeek should === (ZonedDateTime.now.minusDays(3).getDayOfWeek)
    }

    "convert '21-03-1999' to correct date" in {
      val d1 = TimeUtil.wordToDate("21-03-1999")
      d1.get.getDayOfMonth should === (21)
      d1.get.getMonthValue should === (3)
      d1.get.getYear should === (1999)
    }

    "convert '1999-03-21-03' to correct date" in {
      val d1 = TimeUtil.wordToDate("1999-03-21")
      d1.get.getDayOfMonth should === (21)
      d1.get.getMonthValue should === (3)
      d1.get.getYear should === (1999)
    }

    "convert '1437875677349' to correct date" in {
      val d1 = TimeUtil.wordToDate("1437875677349")
      info(s"${d1}")
      d1.get.getDayOfMonth should === (26)
      d1.get.getMonthValue should === (7)
      d1.get.getYear should === (2015)
    }

    "convert '2023-03-21_00:00:00' to correct date and time" in {
      val d1 = TimeUtil.wordToDate("2023-03-21_00:00:00")
      d1.get.getDayOfMonth should === (21)
      d1.get.getMonthValue should === (3)
      d1.get.getYear should === (2023)
      d1.get.getHour should === (0)
    }

    """parse  'cmd text "w1 w2 w3 w4 " end' as 4 stirngs""" in {
      val ss = TimeUtil.parseText("""cmd text "w1 w2 w3 w4 " end""")
      ss.size should === (4)
    }

    """parse 'cmd verb "1 2" ' as 3 strings""" in {
      val ss = TimeUtil.parseText("""cmd verb "1 2" """)
      ss.size should === (3)
    }

    """parse '"cmd 1 2" str2' as 2 strings""" in {
      val ss = TimeUtil.parseText(""""cmd 1 2" str2""")
      ss.size should === (2)
    }

    """parse '"cmd"' as 1 string""" in {
      val ss = TimeUtil.parseText(""""cmd"""")
      ss.size should === (1)
    }

    """parse 'cmd' as 1 string""" in {
      val ss = TimeUtil.parseText("""cmd""")
      ss.size should === (1)
    }

    """parse '"str 1" "str 2" ' as 1 string""" in {
      val ss = TimeUtil.parseText(""""str 1" "str 2" """)
      info(s"result = ${ss.toList}")
      ss.size should === (2)
    }

    "convert human time to milliseconds" in {
      // Plain numbers as milliseconds
      TimeUtil.humanToMillis("1") should === (1L)
      TimeUtil.humanToMillis("1000") should === (1000L)
      TimeUtil.humanToMillis("60000") should === (60000L)
      TimeUtil.humanToMillis(" 500 ") should === (500L)  // with spaces

      TimeUtil.humanToMillis("888123000000123") should === (888123000000123L)

      TimeUtil.humanToMillis("1ms") should === (1L)
      TimeUtil.humanToMillis("1msec") should === (1L)
      TimeUtil.humanToMillis("1millisecond") should === (1L)
      TimeUtil.humanToMillis("1milliseconds") should === (1L)
      TimeUtil.humanToMillis("1 ms") should === (1L)
      TimeUtil.humanToMillis("1  msec") should === (1L)
      TimeUtil.humanToMillis("1  millisecond") should === (1L)
      TimeUtil.humanToMillis("1 milliseconds") should === (1L)
      
      // Regular time units
      TimeUtil.humanToMillis("1s") should === (1000L)
      TimeUtil.humanToMillis("1m") should === (60 * 1000L)
      TimeUtil.humanToMillis("1h") should === (60 * 60 * 1000L)
      TimeUtil.humanToMillis("1d") should === (24 * 60 * 60 * 1000L)
      TimeUtil.humanToMillis("1w") should === (7 * 24 * 60 * 60 * 1000L)
      // not supported not to mix with minutes
      // TimeUtil.humanToMillis("1M") should === (30 * 24 * 60 * 60 * 1000L)
      TimeUtil.humanToMillis("1y") should === (365 * 24 * 60 * 60 * 1000L)
      
      // Full word units
      TimeUtil.humanToMillis("1sec") should === (1000L)
      TimeUtil.humanToMillis("1min") should === (60 * 1000L)
      TimeUtil.humanToMillis("1hour") should === (60 * 60 * 1000L)
      TimeUtil.humanToMillis("1day") should === (24 * 60 * 60 * 1000L)
      TimeUtil.humanToMillis("1week") should === (7 * 24 * 60 * 60 * 1000L)
      TimeUtil.humanToMillis("1month") should === (30 * 24 * 60 * 60 * 1000L)
      TimeUtil.humanToMillis("1year") should === (365 * 24 * 60 * 60 * 1000L)
    }

    "convert complex human time expressions to milliseconds" in {
      TimeUtil.humanToMillis("1h30m") should === ((90 * 60 * 1000L))
      TimeUtil.humanToMillis("2d12h") should === ((60 * 60 * 1000L) * (48 + 12))
      TimeUtil.humanToMillis("1w2d") should === ((9 * 24 * 60 * 60 * 1000L))
      TimeUtil.humanToMillis("1y6months") should === ((365 + 180) * 24 * 60 * 60 * 1000L)
      
      // Full word combinations
      TimeUtil.humanToMillis("1hour30min") should === ((90 * 60 * 1000L))
      TimeUtil.humanToMillis("1minute30seconds") should === ((90 * 1000L))
      TimeUtil.humanToMillis("2days12hours") should === ((60 * 60 * 1000L) * (48 + 12))
      TimeUtil.humanToMillis("1week2days") should === ((9 * 24 * 60 * 60 * 1000L))
      
      // Mixed formats
      TimeUtil.humanToMillis("1hour30m") should === ((90 * 60 * 1000L))
      TimeUtil.humanToMillis("1h30min") should === ((90 * 60 * 1000L))
      TimeUtil.humanToMillis("1minute30s") should === ((90 * 1000L))
    }

    "handle invalid human time formats" in {
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("invalid")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("-123")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("ms")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1invalid")
      
      // Invalid parts in expressions should throw
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1h30invalid")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1h 30m invalid")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("1h invalid 30m")
      an [IllegalArgumentException] should be thrownBy TimeUtil.humanToMillis("invalid 1h 30m")
    }

    "handle whitespace in human time expressions" in {
      TimeUtil.humanToMillis("1h 30m") should === (TimeUtil.humanToMillis("1h30m"))
      TimeUtil.humanToMillis(" 1d ") should === (TimeUtil.humanToMillis("1d"))
      TimeUtil.humanToMillis("2h  30m") should === (TimeUtil.humanToMillis("2h30m"))
      TimeUtil.humanToMillis("1 hour 30 minutes") should === (TimeUtil.humanToMillis("1h30m"))
      TimeUtil.humanToMillis("1 day  12 hours") should === (TimeUtil.humanToMillis("1d12h"))
      
      // Additional space-related test cases
      TimeUtil.humanToMillis("1 hour") should === (TimeUtil.humanToMillis("1h"))
      TimeUtil.humanToMillis("2 hours") should === (TimeUtil.humanToMillis("2h"))
      TimeUtil.humanToMillis("1 minute") should === (TimeUtil.humanToMillis("1m"))
      TimeUtil.humanToMillis("5 minutes") should === (TimeUtil.humanToMillis("5m"))
      TimeUtil.humanToMillis("1 second") should === (TimeUtil.humanToMillis("1s"))
      TimeUtil.humanToMillis("30 seconds") should === (TimeUtil.humanToMillis("30s"))
      TimeUtil.humanToMillis("1 day") should === (TimeUtil.humanToMillis("1d"))
      TimeUtil.humanToMillis("7 days") should === (TimeUtil.humanToMillis("7d"))
      TimeUtil.humanToMillis("1 week") should === (TimeUtil.humanToMillis("1w"))
      TimeUtil.humanToMillis("2 weeks") should === (TimeUtil.humanToMillis("2w"))
      TimeUtil.humanToMillis("1 month") should === (TimeUtil.humanToMillis("1month"))
      TimeUtil.humanToMillis("6 months") should === (TimeUtil.humanToMillis("6month"))
      TimeUtil.humanToMillis("1 year") should === (TimeUtil.humanToMillis("1y"))
      TimeUtil.humanToMillis("2 years") should === (TimeUtil.humanToMillis("2y"))
      
      // Mixed spaces and plural forms
      TimeUtil.humanToMillis("2 hours 30 minutes") should === (TimeUtil.humanToMillis("2h30m"))
      TimeUtil.humanToMillis("1 day 6 hours 30 minutes") should === (TimeUtil.humanToMillis("1d6h30m"))
      TimeUtil.humanToMillis("1 week 2 days") should === (TimeUtil.humanToMillis("1w2d"))
      TimeUtil.humanToMillis("1 year 6 months") should === (TimeUtil.humanToMillis("1y6month"))
    }

  }
  
}
