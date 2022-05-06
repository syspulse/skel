package io.syspulse.skel.config

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class DataUnit(v:Double,unit:String)
case class Data(ts:Long,v:DataUnit)
case class DataList(name:String,data:List[Data])

class ConfigurationSpec extends AnyWordSpec with Matchers {
  
  "Configuration" should {

    "return multiple args parameters" in {
      val args = Array("1","2","3")
      val c = Configuration.withPriority(Seq(
        new ConfigurationArgs(args,"test-1","",
          ArgString('s', "param.str",""),
          ArgInt('i', "param.int",""),
          ArgParam("<params...>")
        )
      ))

      c.getAll().size should === (3)

      c.getParams().size should === (3)
      c.getParams() should === (Seq("1","2","3"))
    }
    
  }
}
