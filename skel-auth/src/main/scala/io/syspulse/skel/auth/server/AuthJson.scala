package io.syspulse.skel.auth.server

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives

import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.auth._

import io.syspulse.skel.auth.server.{AuthCreateRes, Auths, AuthActionRes, AuthIdp, AuthWithProfileRes}
import spray.json.PrettyPrinter

object AuthJson extends JsonCommon  {  
  import DefaultJsonProtocol._
  implicit val printer = PrettyPrinter

  implicit val jf_Auth = jsonFormat7(Auth.apply)
  implicit val jf_Auths = jsonFormat1(Auths)

  implicit val jf_ActionRsp = jsonFormat2(AuthActionRes)
  implicit val jf_CreateAuthRsp = jsonFormat1(AuthCreateRes)

  implicit val jf_authidp = jsonFormat4(AuthIdp)
  implicit val jf_authprof = jsonFormat10(AuthWithProfileRes)

  implicit val jf_aperm = jsonFormat1(AuthPermissions)
}
