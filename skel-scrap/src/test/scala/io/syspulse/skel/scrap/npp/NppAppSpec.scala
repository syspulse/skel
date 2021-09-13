package io.syspulse.skel.scrap.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util

class NppAppSpec extends WordSpec with Matchers {

  "NppAppSpec" should {
    "load real data from 'http://www.srp.ecocentre.kiev.ua/MEDO-PS'" ignore {

      val npp = new NppScrap(rootUrl="http://www.srp.ecocentre.kiev.ua/MEDO-PS", delay=5000L,delayVariance=10000L)
      
      // val rr = npp.scrap()
      // rr.foreach( r => info(s"${r}"))

    }
  }
}
