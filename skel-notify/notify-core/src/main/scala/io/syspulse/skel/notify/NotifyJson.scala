package io.syspulse.skel.notify

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.notify.Notify

import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object NotifyJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Notify = jsonFormat9(Notify)
  implicit val jf_Notifys = jsonFormat2(Notifys)
  implicit val jf_NotifyRes = jsonFormat1(NotifyRes)
  
  implicit val jf_CreateReq = jsonFormat6(NotifyReq)
  implicit val jf_ActionRes = jsonFormat2(NotifyActionRes)

  implicit val jf_AckNot = jsonFormat1(NotifyAckReq)
}
