package io.syspulse.skel.yell.server

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.yell.Yell
import io.syspulse.skel.yell.store.YellRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.yell._

object YellJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Yell = jsonFormat4(Yell.apply _)
  implicit val jf_Yells = jsonFormat1(Yells)
  implicit val jf_YellRes = jsonFormat1(YellRes)
  implicit val jf_CreateReq = jsonFormat3(YellCreateReq)
  implicit val jf_ActionRes = jsonFormat2(YellActionRes)
  
  implicit val jf_RadnomReq = jsonFormat0(YellRandomReq)
  
}
