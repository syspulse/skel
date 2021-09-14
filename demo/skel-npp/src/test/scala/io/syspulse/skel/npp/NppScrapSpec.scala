package io.syspulse.skel.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.flow._

class NppScrapSpec extends WordSpec with Matchers with FlowTestable {
  
  "NppScrapSpec" should {

    "load data for NPP" in {
      val npp = new NppScrap(delay=0L)
      val f = npp.scrap(new Flow[NppData](FlowID(),data=NppData(),pipeline=null,location=flowDir))
      //info(r.toString)
      val r = f.data.popupFiles
      r.size should  === (66)
      r.head.getClass should !== (Radiation.getClass())

      // check unique areas
      r.keys.groupBy(v=>v).size should === (66)
    }

    "load data for NPP with delay==500msec and limit == 2" in {
      val npp = new NppScrap(delay=500L,limit=2)
      val ts0 = System.currentTimeMillis()

      val f = npp.scrap(new Flow[NppData](FlowID(),data=NppData(),pipeline=null,location=flowDir))

      val ts1 = System.currentTimeMillis()
      val r = f.data.popupFiles
      r.size should  === (2)
      (ts1 - ts0) > 500L should === (true)
    }
  }
}
