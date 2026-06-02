package io.syspulse.skel.user.server

import io.syspulse.skel.service.JsonCommon
import io.syspulse.skel.service.JsonMap
import io.syspulse.skel.user.User
import io.syspulse.skel.user.store.UserRegistry._

import spray.json.{DefaultJsonProtocol, JsonFormat, RootJsonFormat}

import io.syspulse.skel.user._
import io.syspulse.skel.user.server.{UserActionRes, Users, UserCreateReq, UserRandomReq, UserRes, UserUpdateReq}

object UserJson extends JsonCommon {

  import DefaultJsonProtocol._

  // Map only — Option[Map] uses DefaultJsonProtocol.optionFormat so omitted JSON fields deserialize as None.
  implicit val jf_metaMap: JsonFormat[Map[String, Any]] = JsonMap.mapFormat

  implicit val jf_User = jsonFormat8(User)
  implicit val jf_Users = jsonFormat1(Users)
  implicit val jf_UserRes = jsonFormat1(UserRes)
  implicit val jf_CreateReq = jsonFormat6(UserCreateReq)
  implicit val jf_UpdateReq = jsonFormat5(UserUpdateReq)
  implicit val jf_ActionRes = jsonFormat2(UserActionRes)

  implicit val jf_RadnomReq = jsonFormat0(UserRandomReq)
  implicit val jf_uuf = jsonFormat4(UserUploadRes)
}
