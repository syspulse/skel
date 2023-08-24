package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util

abstract class Permission(p:String)
case class PermissionOf(p:String) extends Permission(p)
case class PermissionRead() extends Permission("r")
case class PermissionWrite() extends Permission("w")
case class PermissionAll() extends Permission("*")

case class ResourcePermission(r:Resource,pp:Seq[Permission])