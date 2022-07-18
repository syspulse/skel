package io.syspulse.skel.pdf.report.server

import io.syspulse.skel.pdf.report._
import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object ReportJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Report = jsonFormat8(Report)
  implicit val jf_Reports = jsonFormat1(Reports)
  implicit val jf_ReportRes = jsonFormat1(ReportRes)
  implicit val jf_CreateReq = jsonFormat4(ReportCreateReq)
  implicit val jf_ActionRes = jsonFormat2(ReportActionRes)
  
  
}
