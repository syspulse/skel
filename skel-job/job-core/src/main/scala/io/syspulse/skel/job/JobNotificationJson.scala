package io.syspulse.skel.job

import io.syspulse.skel.service.JsonCommon
import spray.json.DefaultJsonProtocol
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object JobNotificationJson extends JsonCommon { 
  import DefaultJsonProtocol._

  implicit val jf_JobNo = jsonFormat8(JobNotification.apply _)  
}
