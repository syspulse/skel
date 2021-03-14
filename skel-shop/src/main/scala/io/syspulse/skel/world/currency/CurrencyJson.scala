package io.syspulse.skel.world.currency

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.world.currency.CurrencyRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object CurrencyJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val dataCurrencyJsonFormat = jsonFormat5(Currency)

  implicit val dataCurrencysJsonFormat = jsonFormat1(Currencys)

  implicit val dataCurrencyCreateJsonFormat = jsonFormat3(CurrencyCreate)

  implicit val dataCurrencyActionPerformedJsonFormat = jsonFormat2(CurrencyActionPerformed)

  implicit val dataClearActionPerformedJsonFormat = jsonFormat2(ClearActionPerformed)
}
