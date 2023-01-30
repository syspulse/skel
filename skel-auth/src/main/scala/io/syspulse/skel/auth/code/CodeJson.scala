package io.syspulse.skel.auth.code

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.auth.code.CodeRegistry._

object CodeJson extends JsonCommon  {
  
  import DefaultJsonProtocol._

  implicit val jf_Code = jsonFormat5(Code.apply)
  implicit val jf_Codes = jsonFormat1(Codes)
  
  implicit val jf_CreateRsp = jsonFormat1(CodeCreateRes)
}
