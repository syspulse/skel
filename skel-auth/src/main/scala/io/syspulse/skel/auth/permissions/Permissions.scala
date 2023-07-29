package io.syspulse.skel.auth.permissions

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class Perm(role:String,perm:String)
case class Permissions(uid:UUID,permissions:Seq[Perm],roles:Seq[String])

