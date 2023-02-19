package io.syspulse.skel.notify

import scala.util.Random

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.syspulse.skel.util.Util

import io.jvm.uuid._

class NotifySyslog(scope:Option[String]) extends NotifyReceiver[Option[_]] {
  
  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scopeOver:Option[String]):Try[Option[_]] = {
    println(s"severity=${severity}:scope=${scopeOver.getOrElse(scope.getOrElse(""))}: title=${title},msg=${msg}")
    Success(None)
  }
}

