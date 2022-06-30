package io.syspulse.skel.user

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.user.UserRegistry._

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

object UserJson extends JsonCommon {
  
  import DefaultJsonProtocol._

  implicit val jf_User = jsonFormat5(User)
  implicit val jf_Users = jsonFormat1(Users)
  implicit val jf_UserRes = jsonFormat1(UserRes)
  implicit val jf_CreateReq = jsonFormat4(UserCreateReq)
  implicit val jf_ActionRes = jsonFormat2(UserActionRes)
  
  implicit val jf_RadnomReq = jsonFormat0(UserRandomReq)
  
}
