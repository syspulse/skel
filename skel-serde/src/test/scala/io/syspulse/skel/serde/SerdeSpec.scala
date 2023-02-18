package io.syspulse.skel.serde

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import java.time._
import io.jvm.uuid._
//import io.syspulse.skel.util.Util

import scala.jdk.CollectionConverters

class SerdeSpec extends AnyWordSpec with Matchers {
  
  "Serde" should {

    "should serialize and deserialize String" in {
      val dd = Serde.serialize("StrData")
      dd.size should !== (0)
      val s = Serde.deserialize[String](dd)
      s should === ("StrData")
    }

    "should serialize and deserialize DataObj" in {
      val ts = ZonedDateTime.now()
      val dd = Serde.serialize(DataObj(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts,"str",10,Long.MaxValue,"data".getBytes))
      dd.size should !== (0)
      val o = Serde.deserialize[DataObj](dd)
      
      //o should === (DataObj(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts,"str",10,Long.MaxValue,"data".getBytes))
      o.id should === (UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"))
      o.ts.toString should === (ts.toString)
      o.str should === ("str")
      o.int should === (10)
      o.long should === (Long.MaxValue)
      o.data should === ("data".getBytes())
    }

    "should serialize and deserialize Lists" in {
      val dd = Serde.serialize(DataList("measure-0",List(Data(10L,DataUnit(1.0,"m/s")),Data(20L,DataUnit(2.0,"kg")))))
      dd.size should !== (0)
      
      val o = Serde.deserialize[DataList](dd)
      o should === (DataList("measure-0",List(Data(10L,DataUnit(1.0,"m/s")),Data(20L,DataUnit(2.0,"kg")))))
      // o.name should === ("measure-0")
      // o.list(0).ts should === (10L)
      // o.list(0).v should === (DataUnit(1.0,"m/s"))
      // o.list(1).ts should === (20L)
      // o.list(1).v should === (DataUnit(2.0,"kg"))      
    }

  }
}
