package io.syspulse.skel.auth.permit

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class Roles(uid:UUID,roles:Seq[String])

case class Permits(role:String,permissions:Seq[String])

