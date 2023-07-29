package io.syspulse.skel.auth.permissions

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

final case class Permissionss(results: immutable.Seq[Permissions])
final case class PermissionsCreateReq(uid:UUID, permissions:Seq[Perm],roles:Seq[String])
final case class PermissionsRes(permissions: Option[Permissions])
final case class PermissionsCreateRes(perm: Permissions)
final case class PermissionsActionRes(status: String,p:Option[UUID])

final case class PermissionsUpdateReq(uid:UUID, permissions:Option[Seq[Perm]])