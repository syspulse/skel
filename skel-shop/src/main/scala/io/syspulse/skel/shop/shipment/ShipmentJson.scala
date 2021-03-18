package io.syspulse.skel.shop.shipment

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.shop.shipment.ShipmentRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object ShipmentJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val dataShipmentJsonFormat = jsonFormat6(Shipment)
  
  implicit val dataShipmentsJsonFormat = jsonFormat1(Shipments)
  
  implicit val dataShipmentCreateJsonFormat = jsonFormat4(ShipmentCreate)
  
  implicit val dataShipmentActionPerformedJsonFormat = jsonFormat2(ShipmentActionPerformed)

  implicit val dataClearActionPerformedJsonFormat = jsonFormat2(ClearActionPerformed)
}
