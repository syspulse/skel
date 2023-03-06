package io.syspulse.skel.util

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import scala.util.Success

class DiffMapSpec extends AnyWordSpec with Matchers {
  
  "DiffMap" should {

    "return all from initial empty Map"  in {
      val m0 = Map[String,Int]()
      val m1 = Map("1"->1,"2"->2,"3"->3)      
      
      val dm0 = new DiffMap[String,Int](m0)
      val r1 = dm0.diff(m1)
      
      r1._1 should === (Set("1","2","3"))
      r1._2 should === (Set())
      r1._3 should === (Set())
    }

    "return new(1,4),old(2),out(3) for (2,3) -> (1,2,4)"  in {
      val m0 = Map("2"->2,"3"->3)      
      val m1 = Map("1"->1,"2"->2,"4"->4)
      
      val dm0 = new DiffMap[String,Int](m0)
      val r1 = dm0.diff(m1)

      r1._1 should === (Set("1","4"))
      r1._2 should === (Set("2"))
      r1._3 should === (Set("3"))
    }

    "return new(4),old(1,2,3),out() for (1,2,3) -> (1,2,3,4)"  in {
      val m0 = Map("1"->1,"2"->2,"3"->3)
      val m1 = Map("1"->1,"2"->2,"3"->3,"4"->4)
      
      val dm0 = new DiffMap[String,Int](m0)
      val r1 = dm0.diff(m1)

      r1._1 should === (Set("4"))
      r1._2 should === (Set("1","2","3"))
      r1._3 should === (Set())
    }

    "return new(),old(),out(1,2,3) for (1,2,3) -> ()"  in {
      val m0 = Map("1"->1,"2"->2,"3"->3)
      val m1 = Map[String,Int]()
      
      val dm0 = new DiffMap[String,Int](m0)
      val r1 = dm0.diff(m1)

      r1._1 should === (Set())
      r1._2 should === (Set())
      r1._3 should === (Set("1","2","3"))
    }

    "return new(),old(1,2,3),out() for (1,2,3) -> (1,2,3)"  in {
      val m0 = Map("1"->1,"2"->2,"3"->3)
      val m1 = Map("1"->1,"2"->2,"3"->3)
      
      val dm0 = new DiffMap[String,Int](m0)
      val r1 = dm0.diff(m1)
      
      r1._1 should === (Set())
      r1._2 should === (Set("1","2","3"))
      r1._3 should === (Set())
    }

  }
}
