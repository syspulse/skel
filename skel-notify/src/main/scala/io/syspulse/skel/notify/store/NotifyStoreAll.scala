package io.syspulse.skel.notify.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.notify._

class NotifyStoreAll(implicit config:Config) extends NotifyStore {
  val log = Logger(s"${this}")
  def +(notify:Notify):Try[NotifyStore] = {     
    
    val (receivers,_,_) = Notification.parseUri(notify.to.getOrElse("").split("\\s+").toList)

    val rr = Notification.send(receivers,notify.subj.getOrElse(""),notify.msg)
    
    log.info(s"${notify}: ${rr}")
    Success(this)
  }
}
