package io.syspulse.skel.syslog

import scala.collection.immutable

import io.jvm.uuid._

final case class SyslogEvent(  
  subj:String,                        // subject 
  msg:String,                         // arbitrary body
  severity:Option[Int]=None,          // severity
  scope:Option[String]=None,          
  from:Option[String]=None,           // from subsystem (user defined)
  id:Option[UUID] = None,             // event id
  cid:Option[String] = None,          // correlation id
  ts:Long = System.currentTimeMillis,  
)


object SyslogEvent {
  type ID = String
  def uid(s:SyslogEvent):ID = s"${s.ts}_${s.severity}_${s.scope}"
}