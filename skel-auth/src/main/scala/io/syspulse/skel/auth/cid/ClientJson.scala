package io.syspulse.skel.auth.cid

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.auth.cid.ClientRegistry._

object ClientJson extends JsonCommon  {
  
  import DefaultJsonProtocol._

  implicit val jf_Client = jsonFormat5(Client.apply _)
  implicit val jf_Clients = jsonFormat1(Clients)
  
  implicit val jf_CreateReq = jsonFormat3(ClientCreateReq)
  implicit val jf_CreateRsp = jsonFormat1(ClientCreateRes)
  implicit val jf_ar = jsonFormat2(ClientActionRes)
}
