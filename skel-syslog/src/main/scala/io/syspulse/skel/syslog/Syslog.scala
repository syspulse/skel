package io.syspulse.skel.syslog

import scala.jdk.CollectionConverters._
import scala.collection.immutable

case class Syslog(ts:Long,level:Int,area:String,text:String)

final case class Syslogs(users: immutable.Seq[Syslog])

final case class SyslogCreateReq(level:Int,area:String,text:String)
final case class SyslogRandomReq()

final case class SyslogActionRes(status: String,id:Option[String])

final case class SyslogRes(user: Option[Syslog])

object Syslog {
  type ID = String
  def uid(y:Syslog):ID = s"${y.ts}_${y.level}_${y.area}"
}