package io.syspulse.skel.syslog

import scala.jdk.CollectionConverters._
import scala.collection.immutable

case class Syslog(ts:Long,severity:Int,area:String,msg:String)

final case class Syslogs(syslogs: Seq[Syslog])

final case class SyslogCreateReq(level:Int,area:String,msg:String)
final case class SyslogRandomReq()
final case class SyslogActionRes(status: String,id:Option[String])

final case class SyslogRes(user: Option[Syslog])

object Syslog {
  type ID = String
  def uid(s:Syslog):ID = s"${s.ts}_${s.severity}_${s.area}"
}