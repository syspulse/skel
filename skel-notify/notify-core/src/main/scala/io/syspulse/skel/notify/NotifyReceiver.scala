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
  def send(no:Notify):Try[R]
}

case class NotifyStdout() extends NotifyReceiver[NotUsed] {
  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[NotUsed] = {
    println(s"severity=${severity}:scope=${scope}: title=${title},msg=${msg}")
    Success(NotUsed)
  }

  def send(no:Notify):Try[NotUsed] = {
    println(s"${no}")
    Success(NotUsed)
  }
}

case class NotifyNone() extends NotifyReceiver[NotUsed] {
  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[NotUsed] = {
    Success(NotUsed)
  }

  def send(no:Notify):Try[NotUsed] = {
    Success(NotUsed)
  }
}

case class NotifyEmbed[R](uri:String,nr: NotifyReceiver[R]) extends NotifyReceiver[R] {
  //override def toString = s"${this.getClass().getSimpleName()}(${nr.toString})"
  def send(title:String,msg:String,severity:Option[NotifySeverity.ID],scope:Option[String]):Try[R] = {
    nr.send(title,msg,severity,scope)
  }

  def send(no:Notify):Try[R] = {
    nr.send(no)
  }

  def getEmbed():NotifyReceiver[R] = nr
}
