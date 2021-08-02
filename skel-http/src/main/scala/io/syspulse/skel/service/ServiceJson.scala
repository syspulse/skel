package io.syspulse.skel.service

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.service.ServiceRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object ServiceJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val serviceJsonFormat = jsonFormat5(Service)
  implicit val servicesJsonFormat = jsonFormat1(Services)
  implicit val serviceCreateJsonFormat = jsonFormat4(ServiceCreate)
  implicit val serviceActionPerformedJsonFormat = jsonFormat2(ServiceActionPerformed)

}
