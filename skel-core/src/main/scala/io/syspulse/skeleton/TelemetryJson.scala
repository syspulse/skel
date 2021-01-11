package io.syspulse.skeleton

import io.syspulse.skeleton.TelemetryRegistry._

import spray.json.DefaultJsonProtocol

import java.util.{UUID}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object TelemetryJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val telemetryJsonFormat = jsonFormat2(Telemetry)
  implicit val telemetriesJsonFormat = jsonFormat1(Telemetries)
  
}
