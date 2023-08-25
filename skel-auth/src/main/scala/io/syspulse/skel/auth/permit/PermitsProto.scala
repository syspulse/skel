package io.syspulse.skel.auth.permit

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

final case class Permitss(results: immutable.Seq[Permits])
final case class PermitsCreateReq(role:String, permissions:Seq[String])
final case class PermitsRes(Permits: Option[Permits])
final case class PermitsCreateRes(permits: Permits)
final case class PermitsActionRes(status: String,p:Option[String])
final case class PermitsUpdateReq(role:String, permissions:Option[Seq[String]])

final case class Roless(results: immutable.Seq[Roles])

final case class RolesCreateReq(uid:UUID, roles:Seq[String])
final case class RolesUpdateReq(uid:UUID, roles:Option[Seq[String]])
final case class RolesActionRes(status: String,p:Option[UUID])