package io.syspulse.skel.syslog.server

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.syslog.Syslog
import io.syspulse.skel.syslog.store.SyslogRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.syslog._

object SyslogJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Syslog = jsonFormat4(Syslog.apply _)
  implicit val jf_Syslogs = jsonFormat1(Syslogs)
  implicit val jf_SyslogRes = jsonFormat1(SyslogRes)
  implicit val jf_CreateReq = jsonFormat3(SyslogCreateReq)
  implicit val jf_ActionRes = jsonFormat2(SyslogActionRes)
  
  implicit val jf_RadnomReq = jsonFormat0(SyslogRandomReq)
  
}
