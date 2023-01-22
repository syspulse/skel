package io.syspulse.skel.otp.server

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.otp._

object OtpJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_Otp = jsonFormat9(Otp)
  implicit val jf_Otps = jsonFormat1(Otps)
  implicit val jf_OtpRes = jsonFormat1(OtpRes)
  implicit val jf_OtpCode = jsonFormat2(OtpCode)

  implicit val jf_CreateReq = jsonFormat6(OtpCreateReq)
  implicit val jf_ActionRes = jsonFormat2(OtpActionRes)
  implicit val jf_CreateRes = jsonFormat2(OtpCreateRes)

  implicit val jf_RadnomReq = jsonFormat6(OtpRandomReq)
  implicit val jf_RandomRes = jsonFormat2(OtpRandomRes)

  implicit val jf_CodeRes = jsonFormat1(OtpCodeRes)
  implicit val jf_CodeVerifyRes = jsonFormat2(OtpCodeVerifyRes)
}
