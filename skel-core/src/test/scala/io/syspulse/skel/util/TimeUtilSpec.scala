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
  }
}
