package io.syspulse.skel.telemetry

import spray.json._
import DefaultJsonProtocol._

import io.syspulse.skel.telemetry._
import io.syspulse.skel.service.JsonCommon

object TelemetryJson extends JsonCommon {
  
  implicit val fmt = jsonFormat3(Telemetry.apply _)
}
