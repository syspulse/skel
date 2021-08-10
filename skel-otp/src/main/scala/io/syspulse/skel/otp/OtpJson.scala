package io.syspulse.skel.otp

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.otp.OtpRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object OtpJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val otpJsonFormat = jsonFormat8(Otp)
  implicit val otpsJsonFormat = jsonFormat1(Otps)
  implicit val otpCreateJsonFormat = jsonFormat5(OtpCreate)
  implicit val otpActionPerformedJsonFormat = jsonFormat2(OtpActionPerformed)
  implicit val otpCreatePerformedJsonFormat = jsonFormat2(OtpCreatePerformed)

  implicit val otpCodeResponseJsonFormat = jsonFormat1(GetOtpCodeResponse)
  implicit val otpCodeVerifyResponseJsonFormat = jsonFormat2(GetOtpCodeVerifyResponse)
}
