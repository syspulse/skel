package io.syspulse.skel.syslog

import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.jvm.uuid._

case class Syslog(
  // ts:Long,
  // severity:Int,
  // area:String,
  // msg:String
  
  msg:String,                         // arbitrary body
  severity:Option[Int]=None,          // severity
  scope:Option[String]=None,          
  from:Option[String]=None,           // from subsystem (user defined)
  id:Option[UUID] = None,             // event id
  cid:Option[String] = None,          // correlation id
  ts:Long = System.currentTimeMillis,
  subj:Option[String] = None,                        // subject 
)

final case class Syslogs(syslogs: Seq[Syslog],total:Long)

final case class SyslogCreateReq(
  //subj:String, msg:String, level:Int, scope:String
  msg:String,                         // arbitrary body
  severity:Option[Int]=None,          // severity
  scope:Option[String]=None,          
  from:Option[String]=None,           // from subsystem (user defined)
  cid:Option[String] = None,          // correlation id
  subj:Option[String] = None, 
)

final case class SyslogRandomReq()
final case class SyslogActionRes(status: String,id:Option[String])

final case class SyslogRes(rsp: Option[Syslog])

object Syslog {
  type ID = String
  def uid(s:Syslog):ID = s"${s.ts}_${s.severity}_${s.scope}"
}