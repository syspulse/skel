package io.syspulse.skel.enroll.flow.phase

import scala.util.Random

import com.typesafe.scalalogging.Logger

import scala.concurrent.duration.Duration
import scala.concurrent.Future
import scala.concurrent.Await
import akka.actor.typed.scaladsl.Behaviors

import io.jvm.uuid._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.notify._
import io.syspulse.skel.notify.client._
import io.syspulse.skel.notify.store._
import io.syspulse.skel.notify.server.NotifyRoutes
import io.syspulse.skel.notify.server.WsNotifyRoutes

import io.syspulse.skel.notify.aws.NotifySNS
import io.syspulse.skel.notify.email.NotifyEmail
import io.syspulse.skel.notify.ws.NotifyWebsocket
import io.syspulse.skel.notify.telegram.NotifyTelegram
import scala.util.Try
import scala.util.Failure
import scala.util.Success

import io.syspulse.skel.enroll.Config

class PhaseFinish(config:Config) extends Phase {
  import io.syspulse.skel.FutureAwaitable._

  def notify(to:String,subj:String,msg:String) = {
    val toUri = s"stdout://${to} email://${to} syslog://enroll"

    log.info(s"Notify(${toUri},${subj},${msg}) -> ${NotifyService.service}")
    
    val r = NotifyService.service
      .withTimeout(timeout)
      .notify(toUri,subj,msg,Some(NotificationSeverity.INFO),None)
      .await()
    
    log.info(s"${toUri}: ${r}")
    r
  }

  def run(data:Map[String,Any]):Try[String] = {
    
    val to = config.notifyEmail
    val uid = data.get("uid").getOrElse("").toString
    val email = data.get("email").getOrElse("").toString
    
    notify(to,s"User enrolled: ${uid}",s"User=${email}") match {
      case Some(n) => Success(n.toString)
      case None => Failure(new Exception(s"failed to notify: ${data}"))
    }    
  }
}