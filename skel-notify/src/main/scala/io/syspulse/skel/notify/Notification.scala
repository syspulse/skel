package io.syspulse.skel.notify

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.notify.aws.NotifySNS
import io.syspulse.skel.notify.email.NotifyEmail
import io.syspulse.skel.notify.ws.NotifyWebsocket
import io.syspulse.skel.notify.telegram.NotifyTelegram

import io.syspulse.skel.util.Util

import io.jvm.uuid._

abstract class NotifyReceiver[R] {
  def send(title:String,msg:String):Try[R]
}

class NotifyStdout() extends NotifyReceiver[Option[_]] {
  def send(title:String,msg:String):Try[Option[_]] = {
    println(s"title=${title},msg=${msg}")
    Success(None)
  }
}

case class Receivers(name:String,receviers:Seq[NotifyReceiver[_]])

object Notification {
  val log = Logger(s"${this}")

  def parseUri(params:List[String])(implicit config:Config):(Receivers,String,String) = {
    var nn = Seq[NotifyReceiver[_]]()
    var data = Seq[String]()
    for( p <- params) {
      if(p.contains("//")) {          
        val n = p.split("://").toList match {
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
          case "tel" :: _ => new NotifyTelegram(p)(config)
          case _ => new NotifyStdout
        }
        nn = nn :+ n
      }
      else 
        data = data :+ p
    }
    val (subj,msg) = data.size match {
      case 0 => ("","")
      case 1 => ("",data(0))
      case _ => (data(0),data(1))
    } 
    (Receivers(s"group-${Util.hex(Random.nextBytes(10))}", nn),subj,msg)
  }

  def send[R](n:NotifyReceiver[R],title:String,msg:String) = {
    log.info(s"($title,$msg)-> ${n}")
    n.send(title,msg)
  }

  def broadcast(n:Seq[NotifyReceiver[_]],title:String,msg:String):Seq[Try[_]] = {
    log.info(s"[$title,$msg]-> ${n}")
    n.map( n => n.send(title,msg)).toSeq
  }

  def send[R](g:Receivers,title:String,msg:String):Seq[Try[_]] = {
    broadcast(g.receviers,title,msg)
  }

}

