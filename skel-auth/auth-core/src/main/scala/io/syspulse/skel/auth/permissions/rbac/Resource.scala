package io.syspulse.skel.auth.permissions.rbac

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util


abstract class Resource(s:String)

case class ResourceOf(r:String) extends Resource(r)
case class ResourceAll() extends Resource("*")
case class ResourceData() extends Resource("data")
case class ResourceApi() extends Resource("api")
