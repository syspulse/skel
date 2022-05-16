package io.syspulse.skel.cli

import scala.util.{Try,Success,Failure}

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import io.jvm.uuid._
import java.time._
import io.syspulse.skel.util.Util


class CliUtilSpec extends AnyWordSpec with Matchers {
  val testDir = this.getClass.getClassLoader.getResource(".").getPath

  "CliUtilSpec" should {

    "convert 'Today' to current day" in {
      val d1 = CliUtil.wordToDate("Today")
      info(s"${d1}")
      d1.get.getDayOfWeek should === (ZonedDateTime.now.getDayOfWeek)
    }

    "convert 'YESterday' to prev day" in {
      val d1 = CliUtil.wordToDate("YESterday")
      info(s"${d1}")
      d1.get.getDayOfWeek should === (ZonedDateTime.now.minusDays(1).getDayOfWeek)
    }

    "convert '3 DAYS ago' to prev 3-rd day" in {
      val d1 = CliUtil.wordToDate("3 DAYS ago")
      info(s"${d1}")
      d1.get.getDayOfWeek should === (ZonedDateTime.now.minusDays(3).getDayOfWeek)
    }

    """parse  'cmd text "w1 w2 w3 w4 " end' as 4 stirngs""" in {
      val ss = CliUtil.parseText("""cmd text "w1 w2 w3 w4 " end""")
      info(s"result = ${ss.toList}")
      ss.size should === (4)
    }

    """parse 'cmd verb "1 2" ' as 3 strings""" in {
      val ss = CliUtil.parseText("""cmd verb "1 2" """)
      info(s"result = ${ss.toList}")
      ss.size should === (3)
    }

    """parse '"cmd 1 2" str2' as 2 strings""" in {
      val ss = CliUtil.parseText(""""cmd 1 2" str2""")
      info(s"result = ${ss.toList}")
      ss.size should === (2)
    }

    """parse '"cmd"' as 1 string""" in {
      val ss = CliUtil.parseText(""""cmd"""")
      info(s"result = ${ss.toList}")
      ss.size should === (1)
    }

    """parse 'cmd' as 1 string""" in {
      val ss = CliUtil.parseText("""cmd""")
      info(s"result = ${ss.toList}")
      ss.size should === (1)
    }

    """parse '"str 1" "str 2" ' as 1 string""" in {
      val ss = CliUtil.parseText(""""str 1" "str 2" """)
      info(s"result = ${ss.toList}")
      ss.size should === (2)
    }
  }
}
