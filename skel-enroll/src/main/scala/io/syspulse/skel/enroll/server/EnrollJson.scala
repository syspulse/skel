package io.syspulse.skel.enroll.server

import io.syspulse.skel.enroll._
import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object EnrollJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Enroll = jsonFormat5(Enroll)
  implicit val jf_Enrolls = jsonFormat1(Enrolls)
  implicit val jf_EnrollRes = jsonFormat1(EnrollRes)
  implicit val jf_CreateReq = jsonFormat3(EnrollCreateReq)
  implicit val jf_ActionRes = jsonFormat2(EnrollActionRes)
  
  
}
