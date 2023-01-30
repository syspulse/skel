package io.syspulse.skel.auth.cred

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

final case class Creds(clients: immutable.Seq[Cred])
final case class CredCreateReq(cid:Option[String], secret:Option[String], name:Option[String])
final case class CredRes(cid: Option[Cred])
final case class CredCreateRes(cid: Cred)
final case class CredActionRes(status: String,cid:Option[String])
