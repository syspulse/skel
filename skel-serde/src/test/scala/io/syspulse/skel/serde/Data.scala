package io.syspulse.skel.serde

import java.time._
import io.jvm.uuid._

import scala.jdk.CollectionConverters

import upickle.default.{ReadWriter => RW, macroRW}

case class DataUnit(v:Double,unit:String)
case class Data(ts:Long,v:DataUnit)
case class DataList(name:String,list:List[Data])

case class DataObj(id:UUID,ts:ZonedDateTime,str:String,int:Int,long:Long,data:Array[Byte])

case class DataAvroObj(id:UUID,ts:String,str:String,int:Int,long:Long,data:Array[Byte])

case class DataObjMsgPack(id:UUID,ts:String,str:String,int:Int,long:Long,data:Array[Byte])
object DataObjMsgPack {
  implicit val rw: RW[DataObjMsgPack] = macroRW
}

object DataUnit {
  implicit val rw: RW[DataUnit] = macroRW
}

object Data {
  implicit val rw: RW[Data] = macroRW
}

object DataList {
  implicit val rw: RW[DataList] = macroRW
}


