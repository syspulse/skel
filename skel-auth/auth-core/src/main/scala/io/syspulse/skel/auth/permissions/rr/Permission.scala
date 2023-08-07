package io.syspulse.skel.auth.permissions.rr

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util

abstract class Permisson(s:String)
case class PermissionRead() extends Permisson("r")
case class PermissionWrite() extends Permisson("w")
case class PermissionAll() extends Permisson("*")

case class ResourcePermission(r:Resource,pp:Seq[Permisson])