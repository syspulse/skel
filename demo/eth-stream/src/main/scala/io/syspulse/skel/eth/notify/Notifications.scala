package io.syspulse.skel.eth.notify

import scala.util.Random

import com.typesafe.scalalogging.Logger

import java.time.LocalDateTime
import java.time.ZonedDateTime
import scala.util.Try
import scala.util.Success


abstract class NotficationDest(enabled:Boolean = true) {
  def isEnabled:Boolean = enabled
  def formatText(txt:String) = s"${ZonedDateTime.now()}: ${txt}}"
  def send(txt:String):Try[String]
}
case class NotficationEmail(email:String,enabled:Boolean = true) extends NotficationDest(enabled) {
  def send(txt:String) = Success(s"${formatText(txt)}-> Email(${email})")
}
case class NotficationPush(pid:String,enabled:Boolean = false) extends NotficationDest(enabled) {
  def send(txt:String) = Success(s"${formatText(txt)}-> PUSH(${pid})")
}


