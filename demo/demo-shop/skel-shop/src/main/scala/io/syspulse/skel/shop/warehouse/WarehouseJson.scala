package io.syspulse.skel.shop.warehouse

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.shop.warehouse.WarehouseRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object WarehouseJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val dataWarehouseJsonFormat = jsonFormat5(Warehouse)
  
  implicit val dataWarehousesJsonFormat = jsonFormat1(Warehouses)
  
  implicit val dataWarehouseCreateJsonFormat = jsonFormat3(WarehouseCreate)
  
  implicit val dataWarehouseActionPerformedJsonFormat = jsonFormat2(WarehouseActionPerformed)

  implicit val dataClearActionPerformedJsonFormat = jsonFormat2(ClearActionPerformed)
}
