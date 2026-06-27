package io.syspulse.skel.telemetry.ext

import io.syspulse.skel.service.JsonCommon
import spray.json._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives

object TelemetryExtJson extends JsonCommon {
  
  implicit val jf_chain = jsonFormat6(Chain)
  implicit val jf_telemetry = jsonFormat2(TelemetryChain)  

  implicit val jf_telemetry_ext = jsonFormat3(TelemetryExt)  
}
