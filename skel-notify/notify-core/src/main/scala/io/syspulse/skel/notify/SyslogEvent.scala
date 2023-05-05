package io.syspulse.skel.notify

import scala.collection.immutable

import io.jvm.uuid._

final case class SyslogEvent(
  subj:String,
  msg:String,
  ts:Long, 
  severity:Option[NotifySeverity.ID]=None,
  scope:Option[String]=None,
  from:Option[String]
)
