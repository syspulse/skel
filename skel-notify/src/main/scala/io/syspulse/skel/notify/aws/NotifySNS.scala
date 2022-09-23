package io.syspulse.skel.notify.aws

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify.NotifyReceiver

class NotifySNS(arn:String) extends NotifyReceiver[String] with SNS {
  val log = Logger(s"${this}")

  def send(title:String,msg:String):Try[String] = {
    publish(msg,arn).map(_.getMessageId)
  }
}
