package io.syspulse.skel.notify

import scala.collection.immutable

import io.jvm.uuid._

final case class Notify(
    to:Option[String] = None, 
    subj:Option[String] = None,
    msg:String = "", 
    ts:Long = System.currentTimeMillis(), 
    id:UUID = UUID.random,
    severity:Option[NotifySeverity.ID]=None,
    scope:Option[String]=None
)
final case class Notifys(notifys: immutable.Seq[Notify],total:Option[Long])

final case class NotifyReq(to:Option[String] = None, subj:Option[String] = None, msg:String = "", severity:Option[NotifySeverity.ID]=None, scope:Option[String]=None)

final case class NotifyActionRes(status: String,id:Option[UUID])

final case class NotifyRes(id: Option[Notify])