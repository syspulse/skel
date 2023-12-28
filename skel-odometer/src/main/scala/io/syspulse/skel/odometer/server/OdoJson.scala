package io.syspulse.skel.odometer.server

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.odometer.Odo
import io.syspulse.skel.odometer.store.OdoRegistry._

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.odometer._
import io.syspulse.skel.odometer.server.{Odos, OdoCreateReq, OdoRes, OdoUpdateReq}

object OdoJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_odo = jsonFormat3(Odo)
  
  implicit val jf_odos = jsonFormat1(Odos)
  implicit val jf_odo_res = jsonFormat1(OdoRes)
  implicit val jf_odo_CreateReq = jsonFormat2(OdoCreateReq)
  implicit val jf_odo_UpdateReq = jsonFormat2(OdoUpdateReq)
}
