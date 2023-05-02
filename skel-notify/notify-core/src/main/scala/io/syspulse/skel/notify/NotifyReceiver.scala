package io.syspulse.skel.notify

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._
import akka.NotUsed

abstract class NotifyReceiver[R] {
  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[R]
}

class NotifyStdout() extends NotifyReceiver[NotUsed] {
  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[NotUsed] = {
    println(s"severity=${severity}:scope=${scope}: title=${title},msg=${msg}")
    Success(NotUsed)
  }
}

