package io.syspulse.skel.shop.order

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.shop.order.OrderRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object OrderJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val dataOrderJsonFormat = jsonFormat4(Order)
  
  implicit val dataOrdersJsonFormat = jsonFormat1(Orders)
  
  implicit val dataOrderCreateJsonFormat = jsonFormat2(OrderCreate)
  
  implicit val dataOrderActionPerformedJsonFormat = jsonFormat2(OrderActionPerformed)

  implicit val dataClearActionPerformedJsonFormat = jsonFormat2(ClearActionPerformed)
}
