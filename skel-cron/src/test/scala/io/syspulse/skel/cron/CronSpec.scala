package io.syspulse.skel.cron

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import scala.util.{Try,Success,Failure}
import java.time._
// import io.syspulse.skel.util.Util

class CronSpec extends AnyWordSpec with Matchers {
  
  "Cron" should {

    "NOT schedule events for: '0 0/5 * * * ?'" in {
      var n = 0
      val c = new Cron((elaped:Long) => {
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
      val c = new Cron((elaped:Long) => {
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
}
