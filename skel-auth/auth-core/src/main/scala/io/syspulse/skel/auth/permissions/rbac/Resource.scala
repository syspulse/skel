package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util


abstract class Resource(r:String) {
  override def equals(obj: Any): Boolean = obj match {
    case other: Resource => other.get == this.r
    case _ => false
  }
    
  override def hashCode(): Int = r.hashCode()

  def get = r
}

case class ResourceOf(r:String) extends Resource(r)
case class ResourceAll() extends Resource("*")
case class ResourceData() extends Resource("data")
case class ResourceApi() extends Resource("api")
