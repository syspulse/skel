package io.syspulse.skel.auth.cid

import scala.collection.immutable
import io.jvm.uuid._
import io.syspulse.skel.util.Util

final case class Clients(clients: immutable.Seq[Client])
final case class ClientCreateReq(cid:Option[String], secret:Option[String], name:Option[String])
final case class ClientRes(cid: Option[Client])
final case class ClientCreateRes(cid: Client)
final case class ClientActionRes(status: String,cid:Option[String])
