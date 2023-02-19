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

class PhaseSNSSend() extends Phase {
  import io.syspulse.skel.FutureAwaitable._

  def send(to:String,subj:String,msg:String) = {
    val toUri = s"sns://"

    log.info(s"Sending email(${toUri},${to},${subj},${msg}) -> ${NotifyService.service}")
    
    val r = NotifyService.service
      .withTimeout(timeout)
      .notify(toUri,subj,msg,None,None)
      .await()
    
    log.info(s"${toUri}: ${r}")
    r
  }

  def run(data:Map[String,Any]):Try[String] = {
    val to = data.get("email")
    val code = data.get("code")
    to match {
      case Some(to) => 
        send(to.toString,"Confirm your Sign-up",s"Confirm your email with code: ${code}") match {
          case Some(n) => Success(n.toString)
          case None => Failure(new Exception(s"failed to send email"))
        }
      case None => Failure(new Exception(s"attribute not found: 'email'"))
    }    
  }
}

object PhaseSNSSend {
  def apply():PhaseSNSSend = new PhaseSNSSend()
}