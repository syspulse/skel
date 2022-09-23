package io.syspulse.skel.notify

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

class NotifyEmail(to:String) extends NotifyReceiver[String] {
  val log = Logger(s"${this}")

  def sendEmail(to:String,title:String,msg:String):Try[String] = {
    log.info(s"sending email -> $to")
    Success(s"${to}: OK")
  }

  def send(title:String,msg:String):Try[String] = {
    val r = sendEmail(to,title,msg)
    r
  }
}
