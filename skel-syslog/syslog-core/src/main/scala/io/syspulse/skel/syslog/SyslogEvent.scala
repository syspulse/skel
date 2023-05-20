package io.syspulse.skel.syslog

import scala.collection.immutable

import io.jvm.uuid._

final case class SyslogEvent(  
  subj:String,
  msg:String,
  severity:Option[Int]=None,
  scope:Option[String]=None,
  from:Option[String]=None,
  id:Option[UUID] = None,
  ts:Long = System.currentTimeMillis,  
)
