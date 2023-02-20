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
import io.syspulse.skel.notify.kafka.NotifyKafka

case class Receivers(name:String,receviers:Seq[NotifyReceiver[_]])

object Notification {
  val log = Logger(s"${this}")

  def parseUri(params:List[String])(implicit config:Config):(Receivers,String,String) = {
    var nn = Seq[NotifyReceiver[_]]()
    var data = Seq[String]()
    for( p <- params) {
      if(p.contains("//")) {          
        val n = NotifyUri(p)
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

  def send[R](n:NotifyReceiver[R],title:String,msg:String,severity:Option[Int],scope:Option[String]) = {
    log.info(s"Notification(${severity},${scope},$title,$msg)-> ${n}")
    n.send(title,msg,severity,scope)
  }

  def broadcast(nn:Seq[NotifyReceiver[_]],title:String,msg:String,severity:Option[Int],scope:Option[String]):Seq[Try[_]] = {
    log.info(s"Notification(${severity},${scope},$title,$msg)-> ${nn}")
    nn.map( n => n.send(title,msg,severity,scope)).toSeq
  }

  // def broadcast[R](rr:Receivers,title:String,msg:String,severity:Option[Int],scope:Option[String]):Seq[Try[_]] = {
  //   broadcast(rr.receviers,title,msg,severity,scope)
  // }

}

