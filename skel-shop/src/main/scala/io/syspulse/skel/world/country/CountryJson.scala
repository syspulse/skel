package io.syspulse.skel.world.country

import io.syspulse.skel.service.JsonCommon

import io.syspulse.skel.world.country.CountryRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object CountryJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val dataCountryJsonFormat = jsonFormat4(Country)
  
  implicit val dataCountrysJsonFormat = jsonFormat1(Countrys)
  
  implicit val dataCountryCreateJsonFormat = jsonFormat3(CountryCreate)
  
  implicit val dataCountryActionPerformedJsonFormat = jsonFormat2(CountryActionPerformed)

  implicit val dataClearActionPerformedJsonFormat = jsonFormat2(ClearActionPerformed)
}
