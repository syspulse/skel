package io.syspulse.skel.serde

import org.scalatest.{Ignore}
import org.scalatest.wordspec.{ AnyWordSpec}
import org.scalatest.matchers.should.{ Matchers}
import org.scalatest.flatspec.AnyFlatSpec

import upickle._
import upickle.default.{ReadWriter => RW, macroRW}

import java.time._
import io.jvm.uuid._
//import io.syspulse.skel.util.Util

import scala.jdk.CollectionConverters


class MsgPackSerdeSpec extends AnyWordSpec with Matchers {
  import DataObjMsgPack._

  "MsgPackSerdeSpec" should {

    "should serialize and deserialize String" in {
      val dd = upack.write(upack.Str("StrData"))
      dd.size should !== (0)
      val s = upack.read(dd)
      s should === (upack.Str("StrData"))
    }

    "should serialize and deserialize DataObj" in {
      val ts = ZonedDateTime.now()
      val msg = upickle.default.writeMsg(
        DataObjMsgPack(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts.toString,"str",10,Long.MaxValue,"data".getBytes)
      )
      val dd = upack.write(msg)
      dd.size should !== (0)
      val o = upickle.default.readBinary[DataObjMsgPack](dd)
      //o should === (DataObjMsgPack(UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"),ts.toString,"str",10,Long.MaxValue,"data".getBytes))
      o.id should === (UUID("c3ce9adb-8008-426a-8828-6dfdf732df95"))
      o.ts.toString should === (ts.toString)
      o.str should === ("str")
      o.int should === (10)
      o.long should === (Long.MaxValue)
      o.data should === ("data".getBytes())
    }

    "should serialize and deserialize Lists via Msg" in {
      val msg = upickle.default.writeMsg(
        DataList("measure-0",List(Data(10L,DataUnit(1.0,"m/s")),Data(20L,DataUnit(2.0,"kg"))))
      )
      val dd = upack.write(msg)
      dd.size should !== (0)
      
      val o = upickle.default.readBinary[DataList](dd)
      o should === (DataList("measure-0",List(Data(10L,DataUnit(1.0,"m/s")),Data(20L,DataUnit(2.0,"kg")))))
    }

    "should serialize and deserialize Lists via Binary (performance)" in {
      val dd = upickle.default.writeBinary(
        DataList("measure-0",List(Data(10L,DataUnit(1.0,"m/s")),Data(20L,DataUnit(2.0,"kg"))))
      )
      dd.size should !== (0)
      
      val o = upickle.default.readBinary[DataList](dd)
      o should === (DataList("measure-0",List(Data(10L,DataUnit(1.0,"m/s")),Data(20L,DataUnit(2.0,"kg")))))
    }

  }
}
