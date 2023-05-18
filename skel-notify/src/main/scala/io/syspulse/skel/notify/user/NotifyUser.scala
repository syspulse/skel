package io.syspulse.skel.notify.user

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify.NotifyReceiver
import io.syspulse.skel.notify.server.WS
import io.syspulse.skel.notify.NotifySeverity

import io.syspulse.skel.syslog.SyslogEvent
import io.syspulse.skel.syslog.SyslogEventJson
import io.syspulse.skel.notify.Notify

class NotifyUser(user:Option[String] = None) extends NotifyReceiver[Long] {
  val log = Logger(s"${this}")

  import spray.json._
  import SyslogEventJson._

  // scope is user id or global
  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[Long] = {
    val u = if(user.isDefined) user else scope
    log.info(s"-> User(${u})")

    val loggedUsers = u match {
      case None | Some("sys.all") => 
        // all users, but get only connected users
        WS.all(id = "user")
      case Some(uid) => 
        Seq(uid)
    }

    log.info(s"Logged: ${loggedUsers}")
    // update users push queue

    val ts = System.currentTimeMillis
    loggedUsers.foreach{ uid => {
      //val m = s"""{"ts":${ts},"title": "${title}","msg": "${msg}","severity":"${severity.getOrElse(NotifySeverity.INFO)}"}"""
      val m = SyslogEvent( subj = title, msg, severity, scope, from = None ).toJson.compactPrint
      WS.broadcast(s"${uid}", title, m, id = "user")
    }}

    Success(loggedUsers.size)
  }

  def send(no:Notify):Try[Long] = {
    send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  }
}

