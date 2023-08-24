package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger
import io.jvm.uuid._
import io.syspulse.skel.util.Util

abstract class Permission(p:String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: Permission => other.get == this.p
    case _ => false
  }
  override def hashCode(): Int = p.hashCode()
  def get = p
}

case class PermissionOf(p:String) extends Permission(p)
case class PermissionRead() extends Permission("read")
case class PermissionWrite() extends Permission("write")
case class PermissionAll() extends Permission("*")

case class ResourcePermission(r:Resource,pp:Seq[Permission])