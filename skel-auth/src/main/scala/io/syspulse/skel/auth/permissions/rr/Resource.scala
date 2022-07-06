package io.syspulse.skel.auth.permissions.rr

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.util.Util


abstract class Resource(s:String)

case class ResourceAll() extends Resource("all")
case class ResourceData() extends Resource("data")

