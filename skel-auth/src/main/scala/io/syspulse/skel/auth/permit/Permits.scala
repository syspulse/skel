package io.syspulse.skel.auth.permit

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class Perm(role:String,perm:String)
// case class Permits(uid:UUID,permissions:Seq[Perm],roles:Seq[String])

case class Permits(uid:UUID,roles:Seq[String])

