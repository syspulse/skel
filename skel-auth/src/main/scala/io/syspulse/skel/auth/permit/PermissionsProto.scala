package io.syspulse.skel.auth.permit

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

final case class Permitss(results: immutable.Seq[Permits])
final case class PermitsCreateReq(uid:UUID, Permits:Seq[Perm],roles:Seq[String])
final case class PermitsRes(Permits: Option[Permits])
final case class PermitsCreateRes(permits: Permits)
final case class PermitsActionRes(status: String,p:Option[UUID])

final case class PermitsUpdateReq(uid:UUID, Permits:Option[Seq[Perm]])