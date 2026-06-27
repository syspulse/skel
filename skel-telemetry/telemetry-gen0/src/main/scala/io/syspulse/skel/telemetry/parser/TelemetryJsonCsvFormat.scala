package io.syspulse.skel.telemetry.parser

import scala.util.{Try,Success,Failure}
import scala.jdk.CollectionConverters._
import scala.util.Random
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.telemetry.{Telemetry,TelemetryLike}
import io.syspulse.skel.store.ExtFormat

// special format for a single value
case class TelemetryDouble(id:Telemetry.ID, ts:Long, v:Double)

object TelemetryDoubleJson extends JsonCommon {
  implicit val fmt = jsonFormat3(TelemetryDouble.apply _)
}


class TelemetryJsonCsvFormat extends ExtFormat[Telemetry] {
  import TelemetryDoubleJson._
  import spray.json._
  import DefaultJsonProtocol._

  def decode(data:String):Try[Seq[Telemetry]] = {
    // try to parse TelemetryDouble first
    try {
      val t = data.parseJson.convertTo[TelemetryDouble]
      Success(Seq(Telemetry( t.id,t.ts,v = List(t.v) )))
    } catch {
      case e:Exception => 
        TelemetryParserDefault.parse(data).map(Seq(_))
    }    
  }

  def encode(e:Telemetry):String = e.toCSV
}

object TelemetryJsonCsvFormat {
  implicit val fmtTag = Some(new TelemetryJsonCsvFormat())
}
