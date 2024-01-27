package io.syspulse.skel.notify.ws

import scala.collection.immutable
import scala.util.{Try,Success,Failure}
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify.NotifyReceiver
import io.syspulse.skel.notify.server.WS
import io.syspulse.skel.notify.Notify

case class NotifyWebsocket(id:String) extends NotifyReceiver[Unit] {
  def send(title:String,msg:String,severity:Option[Int],scope:Option[String]):Try[Unit] = {
    val r = WS.broadcast(id,title,msg)
    Success(r)
  }

  def send(no:Notify):Try[Unit] = {
    send(no.subj.getOrElse(""),no.msg,no.severity,no.scope)
  }
}

