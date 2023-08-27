package io.syspulse.skel.auth.permit

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class PermitUser(uid:UUID,roles:Seq[String],xid:String)

case class PermitResource(res:String,permissions:Seq[String])

case class PermitRole(role:String,resources:Seq[PermitResource])

