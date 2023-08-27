package io.syspulse.skel.auth.permit

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.auth.permit.PermitRegistry._
import io.syspulse.skel.auth.permit.PermitRoles
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}
import io.syspulse.skel.auth.permit.PermitUsers

object PermitJson extends JsonCommon  {
  
  import DefaultJsonProtocol._

  //implicit val jf_perm = jsonFormat2(Perm.apply _)
  implicit val jf_pres = jsonFormat2(PermitResource.apply _)
  implicit val jf_permits = jsonFormat2(PermitRole.apply _)
  implicit val jf_permitss = jsonFormat1(PermitRoles.apply _)

  implicit val jf_pu = jsonFormat3(PermitUser.apply _)
  implicit val jf_pus = jsonFormat1(PermitUsers.apply _)

  implicit val jf_pu_create = jsonFormat3(PermitUserCreateReq.apply _)
}
