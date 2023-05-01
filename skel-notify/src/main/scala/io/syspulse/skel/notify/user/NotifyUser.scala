package io.syspulse.skel.notify.user

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify.NotifyReceiver
import io.syspulse.skel.notify.server.WS

class NotifyUser() extends NotifyReceiver[Long] {
  val log = Logger(s"${this}")

  // scope is user id or global
  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[Long] = {
    log.info(s"-> User(${scope})")
    val loggedUsers = scope match {
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
      val data = s"""{"ts":${ts},"title": "${title}","msg": "${msg}","severity":"${severity}"}"""
      WS.broadcast(s"${uid}", title, data, id = "user")
    }}

    Success(loggedUsers.size)
  }
}

