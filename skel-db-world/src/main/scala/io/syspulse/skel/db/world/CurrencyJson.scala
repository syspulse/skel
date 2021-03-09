package io.syspulse.skel.db.world

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.db.world.CurrencyRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object CurrencyJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val dataCurrencyJsonFormat = jsonFormat2(Currency)

  implicit val dataCurrencysJsonFormat = jsonFormat1(Currencys)

  implicit val dataCurrencyCreateJsonFormat = jsonFormat2(CurrencyCreate)

  implicit val dataCurrencyActionPerformedJsonFormat = jsonFormat2(CurrencyActionPerformed)

}
