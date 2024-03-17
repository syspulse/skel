package io.syspulse.skel.notify.slack

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify.NotifyReceiver
import io.syspulse.skel.notify.server.WS
import io.syspulse.skel.notify.Notify
import io.syspulse.skel.notify.NotifySeverity

case class NotifySlack(channel:String,method:String = "webhook",apiKey:String="",webhookUrl:String = "",timeout:Long = 3000L) extends NotifyReceiver[String] {
  val log = Logger(s"${this}")

  def sendWebhook(msg:String) = {
    val r = requests.post(
      s"https://hooks.slack.com/services/${webhookUrl}", 
      data = s"""{"text":"${msg}"}""",
      headers = Seq("Content-Type" -> "application/json")
    ) 

    log.info(s"rsp=${r.statusCode}")
    r.text()
  }

  def sendChannel(msg:String) = {
    val r = requests.post(
      s"https://slack.com/api/chat.postMessage", 
      data = s"""{"channel":"${channel}","text":"${msg}"}""",
      headers = Seq("Content-Type" -> "application/json; charset=utf-8","Authorization" -> s"Bearer ${apiKey}")
    ) 

    log.info(s"rsp=${r.statusCode}")
    r.text()
  }
  
  def send(subj:String,msg:String,severity:Option[NotifySeverity.ID],scopeOver:Option[String]):Try[String] = {        
    log.info(s"[${msg}] -> slack://${channel}")
    
    val rsp = method match {
      case "webhook" => sendWebhook(msg)
      case "channel" => sendChannel(msg)
      case _ => sendChannel(msg)
    }
    //val rsp = sendWebhook(msg)
    Success(rsp)
  }

  def send(no:Notify):Try[String] = {
    send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  }
}

