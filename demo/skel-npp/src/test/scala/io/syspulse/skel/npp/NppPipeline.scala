package io.syspulse.skel.npp

import scala.util.{Try,Success,Failure}

import org.scalatest.{ Matchers, WordSpec }
import org.scalactic.TolerantNumerics

import java.time._
import io.syspulse.skel.util.Util
import io.syspulse.skel.flow._

class NppPipeline extends WordSpec with Matchers with FlowTestable {
  
  "NppPipeline" should {

    "run Npp Pipeline successfully" in {
      val pipe = new Pipeline[NppData]("NPP-Pipeline",
        stages = List(
          new NppScrap(delay=0L),
          new NppDecode()
        )
      )
      
      val f1 = pipe.run(NppData())
      
      val r = f1.data.radiation
      r.size should  === (66)
      r.head.getClass should !== (Radiation.getClass())

    }
  }
}
