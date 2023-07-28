package io.syspulse.skel.auth.permissions

import io.syspulse.skel.service.JsonCommon

import spray.json.DefaultJsonProtocol

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.server.Directives
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, JsonFormat, deserializationError}

import io.syspulse.skel.auth.permissions.PermissionsRegistry._

object PermissionsJson extends JsonCommon  {
  
  import DefaultJsonProtocol._

  implicit val jf_perm = jsonFormat2(Permissions.apply _)
  implicit val jf_perms = jsonFormat1(Permissionss.apply _)
}
