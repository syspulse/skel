package io.syspulse.skel.notify.aws

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify.NotifyReceiver
import io.syspulse.skel.notify.NotifySeverity
import io.syspulse.skel.notify.Notify

class NotifySNS(arn:String) extends NotifyReceiver[String] with SNS {
  val log = Logger(s"${this}")

  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[String] = {
    publish(
      s"""["title":"${title}","msg":"${msg}","ts":${System.currentTimeMillis()}, "severity": ${severity.getOrElse(0)}, "scope": "${scope.getOrElse("sys.none")}"]""",
      arn).map(_.getMessageId)
  }

  def send(no:Notify):Try[String] = {
    send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  }
}
