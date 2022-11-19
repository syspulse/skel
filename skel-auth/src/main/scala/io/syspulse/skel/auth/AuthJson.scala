package io.syspulse.skel.auth

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.auth.AuthRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object AuthJson extends JsonCommon  {
  
  import DefaultJsonProtocol._

  implicit val jf_Auth = jsonFormat5(Auth.apply)
  implicit val jf_Auths = jsonFormat1(Auths)

  implicit val jf_ActionRsp = jsonFormat2(AuthActionRes)
  implicit val jf_CreateAuthRsp = jsonFormat1(AuthCreateRes)

  implicit val jf_AuthWithProfileRsp = jsonFormat8(AuthWithProfileRes)
  //implicit val jf_ProxyAuthResult = jsonFormat1(ProxyAuthRes)
}
