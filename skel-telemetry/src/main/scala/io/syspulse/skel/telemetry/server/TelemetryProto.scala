package io.syspulse.skel.telemetry.server

import scala.collection.immutable
import spray.json.DefaultJsonProtocol
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.telemetry.Telemetry
import io.syspulse.skel.telemetry.Telemetry.ID
import io.syspulse.skel.telemetry.TelemetryJson
import io.syspulse.skel.service.JsonCommon

final case class Telemetrys(data: immutable.Seq[Telemetry],total:Option[Long]=None)

final case class TelemetryCreateReq(id:Telemetry.ID,ts:Long,data:List[Any])
final case class TelemetryRandomReq()
final case class TelemetryActionRes(status: String,id:Option[Telemetry.ID])
final case class TelemetryRes(telemetry: Option[Telemetry])

object TelemetryProto extends JsonCommon {

  import DefaultJsonProtocol._
  import TelemetryJson._

  implicit val jf_Telemetrys = jsonFormat2(Telemetrys)
  implicit val jf_TelemetryRes = jsonFormat1(TelemetryRes)
  implicit val jf_CreateReq = jsonFormat3(TelemetryCreateReq)
  implicit val jf_ActionRes = jsonFormat2(TelemetryActionRes)
  
  implicit val jf_RadnomReq = jsonFormat0(TelemetryRandomReq)
  
}