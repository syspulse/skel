package io.syspulse.skel.odometer

import scala.util.{Failure,Success,Try}
import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success
import io.syspulse.skel.odometer.store.OdoStoreMem

class OdoStoreMemSpec extends AnyWordSpec with Matchers {
  
  "OdoStoreMem" should {

    "add and get 'group-1:counter-1'" in {
      val odo = new OdoStoreMem()
      val o1 = Odo("group-1:counter-1",100)
      val r1 = odo.+(o1)
      r1 shouldBe a [Success[_]]

      val r2 = odo.??(Seq("group-1:counter-1"))
      r2 should === (Seq(Odo("group-1:counter-1",100,o1.ts)))
    }

    "add 'group-1:counter-1','group-1:counter-2' and get 'group-1:*'" in {
      val odo = new OdoStoreMem()
      val o1 = Odo("group-1:counter-1",100)
      val o2 = Odo("group-1:counter-2",200)
      val r1 = odo.+(o1)
      val r2 = odo.+(o2)
      r1 shouldBe a [Success[_]]
      r2 shouldBe a [Success[_]]

      val r3 = odo.??(Seq("group-1:*"))
      r3 should === (Seq(Odo("group-1:counter-1",100,o1.ts),Odo("group-1:counter-2",200,o2.ts)))
    }

    "add 'group-1:counter-1','group-1:counter-2' and get 'group-2:*'" in {
      val odo = new OdoStoreMem()
      val o1 = Odo("group-1:counter-1",100)
      val o2 = Odo("group-1:counter-2",200)
      val r1 = odo.+(o1)
      val r2 = odo.+(o2)
      r1 shouldBe a [Success[_]]
      r2 shouldBe a [Success[_]]

      val r3 = odo.??(Seq("group-2:*"))
      r3 should === (Seq())
    }

    "add 'group-1:counter-1','group-2:counter-2' and get 'group-2:*'" in {
      val odo = new OdoStoreMem()
      val o1 = Odo("group-1:counter-1",100)
      val o2 = Odo("group-2:counter-2",200)
      val r1 = odo.+(o1)
      val r2 = odo.+(o2)
      r1 shouldBe a [Success[_]]
      r2 shouldBe a [Success[_]]

      val r3 = odo.??(Seq("group-1:*"))
      val r4 = odo.??(Seq("group-2:*"))
      r3 should === (Seq(Odo("group-1:counter-1",100,o1.ts)))
      r4 should === (Seq(Odo("group-2:counter-2",200,o2.ts)))
    }
    
  }

}
