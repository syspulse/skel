package io.syspulse.skel.ingest.flow

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import scala.util.Success

import io.syspulse.skel.util.Util
import io.syspulse.skel.ingest.flow.Flows._

case class DataUnit(v:Double,unit:String)
case class Data(ts:Long,v:DataUnit)
case class DataList(name:String,data:List[Data])

class RotatorSpec extends AnyWordSpec with Matchers {
  
  "Rotator" should {

    "RotatorCurrentTime '/dir/year={yyyy}/month={MM}/day={dd}/file.log' should be rotateable" in {
      val r = new RotatorCurrentTime()
      r.init("/dir/year={yyyy}/month={MM}/day={dd}/file.log",Long.MaxValue,Long.MaxValue)      
      
      r.isRotatable() should === (true)
      r.needRotate(10,1000) should === (false)
    }

    "RotatorCurrentTime '/dir/{ss}/file-{ss}.log' should rotate every second" in {
      val r = new RotatorCurrentTime()
      val f = "/dir/{ss}/file-{ss}.log"
      r.init(f,Long.MaxValue,Long.MaxValue)      
      
      r.isRotatable() should === (true)
      r.needRotate(10,1000) should === (false)
      
      val f1 = r.rotate(f,10,1000)
      info(s"f1 = ${f1}")
      r.needRotate(10,1000) should === (false)

      Thread.sleep(500)
      r.needRotate(15,1500) should === (false)

      Thread.sleep(510)

      r.isRotatable() should === (true)
      r.needRotate(20,2000) should === (true)
      val f2 = r.rotate(f,20,2000)
      info(s"f2 = ${f2}")

      f1 should !== (f2)      
    }

  }
}
