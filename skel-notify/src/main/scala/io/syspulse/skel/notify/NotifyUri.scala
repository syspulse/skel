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
import io.syspulse.skel.notify.syslog.NotifySyslog
import io.syspulse.skel.notify.http.NotifyHttp

import io.syspulse.skel.util.Util

object NotifyUri {

  // cache
  var cache:Map[String,NotifyReceiver[_]] = Map()

  def cacheOrNew(uri:String,n: => NotifyReceiver[_]):NotifyReceiver[_] = {
    cache.get(uri) match {
      case Some(n) => n
      case None => 
        // this is a call to new !!!
        val r = n
        cache = cache + (uri -> r)
        r
    }
  }

  def apply(uri:String)(implicit config:Config):NotifyReceiver[_] = {    
    uri.trim.split("://").toList match {
      case "email" :: dst :: _ => 
        val (smtp,to) = dst.split("/").toList match {
          case smtp :: to :: Nil => (smtp,to)
          case to :: Nil => ("smtp",to)
          case to  => ("smtp",to.mkString(""))
        }
        cacheOrNew(uri,new NotifyEmail(smtp,to)(config))

      case "stdout" :: _ => cacheOrNew(uri,new NotifyStdout)
      case "sns" :: arn :: _ => cacheOrNew(uri,new NotifySNS(arn))
      case "sns" :: Nil => cacheOrNew(uri,new NotifySNS(config.snsUri.split("sns://")(1)))
      case "ws" :: topic :: _ => cacheOrNew(uri,new NotifyWebsocket(topic))
      case "ws" :: _ => cacheOrNew(uri,new NotifyWebsocket(""))
      case "tel" :: _ => cacheOrNew(uri,new NotifyTelegram(uri)(config))
      case "kafka" :: _ => cacheOrNew(uri,new NotifyKafka(uri))
      
      case "syslog" :: Nil => cacheOrNew(uri,new NotifySyslog(None))
      case "syslog" :: channel :: Nil => cacheOrNew(uri,new NotifySyslog(Some(channel)))

      case "http" :: _ => cacheOrNew(uri,new NotifyHttp(uri)(config))
      case "https" :: _ => cacheOrNew(uri,new NotifyHttp(uri)(config))

      case "user" :: Nil => cacheOrNew(uri,new NotifyUser())
      case "user" :: user :: Nil => cacheOrNew(uri,new NotifyUser(Some(user)))

      case "none" :: Nil => cacheOrNew(uri,new NotifyNone)

      case "" :: Nil => cacheOrNew(uri,new NotifyStdout)
      case unknown :: uri :: rest :: Nil => cacheOrNew(uri,new NotifyEmbed(unknown + "://",NotifyUri(uri + "://" + rest)))
      case unknown :: uri :: Nil => cacheOrNew(uri,new NotifyEmbed(unknown + "://",NotifyUri(uri)))
      // expected exception on unknown uri      
    }    
  }

  def isUser(uri:String):Option[String] = {
    uri.trim.split("://").toList match {
      case "user" :: Nil => Some("user.all")
      case "user" :: user :: Nil => Some(user)
      case _ => None
    }    
  }
}

