package io.syspulse.skel.notify.aws

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify.NotifyReceiver

class NotifySNS(arn:String) extends NotifyReceiver[String] with SNS {
  val log = Logger(s"${this}")

  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[String] = {
    publish(
      s"""["title":"${title}","msg":"${msg}","ts":${System.currentTimeMillis()}, "severity": ${severity.getOrElse(0)}, "scope": "${scope.getOrElse("sys.none")}"]""",
      arn).map(_.getMessageId)
  }
}
