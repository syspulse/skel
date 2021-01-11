package io.syspulse.auth.otp

import io.syspulse.skeleton.JsonCommon
import io.syspulse.auth.otp.OtpRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object OtpJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val otpJsonFormat = jsonFormat5(Otp)
  implicit val otpsJsonFormat = jsonFormat1(Otps)
  implicit val otpCreateJsonFormat = jsonFormat4(OtpCreate)
  implicit val otpActionPerformedJsonFormat = jsonFormat1(OtpActionPerformed)

}
