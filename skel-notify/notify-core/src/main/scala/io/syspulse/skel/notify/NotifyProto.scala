package io.syspulse.skel.notify

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.notify.Notify

final case class Notifys(notifys:Seq[Notify],total:Option[Long]=None)

final case class NotifyReq(
  to:Option[String] = None, 
  subj:Option[String] = None, 
  msg:String = "", 
  severity:Option[NotifySeverity.ID]=None, 
  scope:Option[String]=None,
  uid:Option[UUID] = None,
)

final case class NotifyAckReq(id:UUID)

final case class NotifyActionRes(status: String,id:Option[UUID])
final case class NotifyRes(id: Option[Notify])