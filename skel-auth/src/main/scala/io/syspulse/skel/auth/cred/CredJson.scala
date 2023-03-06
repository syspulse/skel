package io.syspulse.skel.auth.cred

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.auth.cred.CredRegistry._

object CredJson extends JsonCommon  {
  
  import DefaultJsonProtocol._

  implicit val jf_Cred = jsonFormat6(Cred.apply _)
  implicit val jf_Creds = jsonFormat1(Creds)
  
  implicit val jf_CreateReq = jsonFormat3(CredCreateReq)
  implicit val jf_CreateRsp = jsonFormat1(CredCreateRes)
  implicit val jf_CredARes = jsonFormat2(CredActionRes)

  implicit val jf_CredToken = jsonFormat2(CredTokenReq)
}
