package io.syspulse.skel.auth

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.auth.AuthRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object AuthJson extends JsonCommon  {
  
  import DefaultJsonProtocol._

  implicit val authJsonFormat = jsonFormat6(Auth.apply)
  implicit val authssJsonFormat = jsonFormat1(Auths)

  implicit val actionPerformedJsonFormat = jsonFormat2(ActionPerformed)
}
