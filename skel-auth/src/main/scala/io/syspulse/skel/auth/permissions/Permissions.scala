package io.syspulse.skel.auth.permissions

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

case class Permissions(uid:UUID,permissions:Seq[String])

