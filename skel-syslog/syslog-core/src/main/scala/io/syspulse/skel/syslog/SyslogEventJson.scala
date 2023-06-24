package io.syspulse.skel.syslog

import io.syspulse.skel.service.JsonCommon

import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object SyslogEventJson extends JsonCommon {  
  import DefaultJsonProtocol._

  implicit val jf_Notify = jsonFormat8(SyslogEvent.apply _)
}
