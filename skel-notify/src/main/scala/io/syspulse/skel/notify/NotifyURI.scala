package io.syspulse.skel.notify

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger
import io.jvm.uuid._

import io.syspulse.skel.notify.aws.NotifySNS
import io.syspulse.skel.notify.email.NotifyEmail
import io.syspulse.skel.notify.ws.NotifyWebsocket
import io.syspulse.skel.notify.telegram.NotifyTelegram
import io.syspulse.skel.notify.kafka.NotifyKafka
import io.syspulse.skel.notify.user.NotifyUser

import io.syspulse.skel.util.Util

object NotifyUri {

  def apply(uri:String)(implicit config:Config):NotifyReceiver[_] = {    
    uri.split("://").toList match {
      case "email" :: dst :: _ => 
        val (smtp,to) = dst.split("/").toList match {
          case smtp :: to :: Nil => (smtp,to)
          case to :: Nil => ("smtp",to)
          case to  => ("smtp",to.mkString(""))
        }
        new NotifyEmail(smtp,to)(config)

      case "stdout" :: _ => new NotifyStdout
      case "sns" :: arn :: _ => new NotifySNS(arn)
      case "sns" :: Nil => new NotifySNS(config.snsUri.split("sns://")(1))
      case "ws" :: topic :: _ => new NotifyWebsocket(topic)
      case "ws" :: _ => new NotifyWebsocket("")
      case "tel" :: _ => new NotifyTelegram(uri)(config)
      case "kafka" :: _ => new NotifyKafka(uri)
      
      case "syslog" :: Nil => new NotifySyslog(None)
      case "syslog" :: scope :: Nil => new NotifySyslog(Some(scope))

      case "http" :: _ => new NotifyHttp(uri)
      case "https" :: _ => new NotifyHttp(uri)

      case "user" :: Nil => new NotifyUser()
      case "user" :: user :: Nil => new NotifyUser(Some(user))

      case _ => new NotifyStdout
    }    
  }
}

