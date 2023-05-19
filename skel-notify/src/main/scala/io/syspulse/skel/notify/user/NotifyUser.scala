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
    send(Notify(
      subj = Some(title),
      msg = msg,
      severity = severity,
      scope = scope
    ))
  }

  def send(no:Notify):Try[Long] = {    
    val u = if(user.isDefined) user else no.scope
    log.info(s"${no}-> User(${u})")

    val loggedUsers = u match {
      case None | Some("user.all") => 
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
      val m = SyslogEvent( 
        subj = no.subj.getOrElse(""), msg = no.msg, severity = no.severity, scope = no.scope, from = no.from.map(_.toString), id = Some(no.id)
      ).toJson.compactPrint
      
      val topic = s"${uid}"
      val channelId = "user"
      WS.broadcast(topic, no.subj.getOrElse(""), m, id = channelId)
    }}

    Success(loggedUsers.size)
  }
}

