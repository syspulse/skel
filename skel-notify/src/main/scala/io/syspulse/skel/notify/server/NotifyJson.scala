package io.syspulse.skel.notify.server

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.notify.Notify
import io.syspulse.skel.notify.store.NotifyRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.notify._

object NotifyJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Notify = jsonFormat5(Notify)
  implicit val jf_Notifys = jsonFormat1(Notifys)
  implicit val jf_NotifyRes = jsonFormat1(NotifyRes)
  implicit val jf_CreateReq = jsonFormat4(NotifyCreateReq)
  implicit val jf_ActionRes = jsonFormat2(NotifyActionRes)
  
  implicit val jf_RadnomReq = jsonFormat0(NotifyRandomReq)
  
}
