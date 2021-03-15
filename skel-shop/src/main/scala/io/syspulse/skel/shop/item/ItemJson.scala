package io.syspulse.skel.shop.item

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.shop.item.ItemRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object ItemJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val dataItemJsonFormat = jsonFormat5(Item)
  
  implicit val dataItemsJsonFormat = jsonFormat1(Items)
  
  implicit val dataItemCreateJsonFormat = jsonFormat3(ItemCreate)
  
  implicit val dataItemActionPerformedJsonFormat = jsonFormat2(ItemActionPerformed)

  implicit val dataClearActionPerformedJsonFormat = jsonFormat2(ClearActionPerformed)
}
