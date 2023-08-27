package io.syspulse.skel.auth.permit

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util
import io.syspulse.skel.auth.permit.{PermitUser, PermitResource, PermitRole}

final case class PermitRoles(results: immutable.Seq[PermitRole])
final case class PermitRoleCreateReq(role:String, resources:Seq[PermitResource])
final case class PermitRoleRes(pr: Option[PermitRole])
final case class PermitRoleCreateRes(permits: PermitRole)
final case class PermitRoleActionRes(status: String,p:Option[String])
final case class PermitRoleUpdateReq(role:String, permissions:Option[Seq[String]])


final case class PermitUsers(results: immutable.Seq[PermitUser])

final case class PermitUserCreateReq(uid:UUID, roles:Seq[String])
final case class PermitUserUpdateReq(uid:UUID, roles:Option[Seq[String]])
final case class PermitUserActionRes(status: String,p:Option[UUID])