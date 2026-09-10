package io.syspulse.skel.wf.ext.event

import spray.json._
import io.syspulse.skel.service.JsonCommon

object EventJson extends JsonCommon {
  implicit val jf_alert: RootJsonFormat[Alert] = jsonFormat16(Alert.apply)
  implicit val jf_create: RootJsonFormat[EventCreateReq] = jsonFormat14(EventCreateReq.apply)
  implicit val jf_alerts: RootJsonFormat[Alerts] = jsonFormat2(Alerts.apply)
  implicit val jf_ev_action: RootJsonFormat[EventActionRes] = jsonFormat2(EventActionRes.apply)
}
