package io.syspulse.skel.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.flow._

class NppRealSpec extends WordSpec with Matchers {

  "NppRealSpec" should {
    "load real data from 'http://www.srp.ecocentre.kiev.ua/MEDO-PS'" ignore {

      val pipe = new Pipeline[NppData]("NPP-Pipeline",
        stages = List(
          new NppScrap(rootUrl="http://www.srp.ecocentre.kiev.ua/MEDO-PS", delay=5000L,delayVariance=5000L),
          new NppDecode(),
          new NppPrint(),
        )
      )
      
      val f1 = pipe.run(NppData())
      
      val r = f1.data.radiation
      r.size should  === (66)
    }
  }
}
