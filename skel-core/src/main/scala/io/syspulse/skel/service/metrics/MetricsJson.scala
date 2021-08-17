package io.syspulse.skel.service.metrics

import io.syspulse.skel.service.metrics.MetricsRegistry._

import spray.json.DefaultJsonProtocol

import io.syspulse.skel.service.JsonCommon

import java.util.{UUID}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object MetricsJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val metricsJsonFormat = jsonFormat2(Metrics)
  implicit val telemetriesJsonFormat = jsonFormat1(Telemetries)
  
}
