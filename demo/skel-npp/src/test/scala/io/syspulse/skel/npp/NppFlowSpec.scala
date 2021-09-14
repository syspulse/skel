package io.syspulse.skel.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.flow._

class NppFlowSpec extends WordSpec with Matchers with FlowTestable {
  
  "NppFlowSpec" should {

    "load and decode data for NPP" in {
      val nppScrap = new NppScrap(delay=0L)
      val nppDecode = new NppDecode()
      
      val f0 = new Flow[NppData](FlowID(),data=NppData(),pipeline=null,location=flowDir)
      val f1 = nppScrap.start(f0)
      val f2 = nppDecode.start(f1)
      
      val f3 = nppScrap.exec(f2)
      val f4 = nppDecode.exec(f3)
      
      val r = f4.data.radiation
      r.size should  === (66)
      r.head.getClass should !== (Radiation.getClass())

    }
  }
}
