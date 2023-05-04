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

abstract class NotifyBroadcast(implicit config:Config) {
  private val log = Logger(s"${this}")
  
  def broadcast(notify:Notify):Try[NotifyBroadcast] = {     
    
    val (receivers,_,_) = Notification.parseUri(notify.to.getOrElse("").split("\\s+").toList)
    try {
      val rr = Notification.broadcast(receivers.receviers, notify.subj.getOrElse(""), notify.msg, notify.severity, notify.scope)
    
      log.info(s"${notify}: ${rr}")
      Success(this)
    } catch {
      case e:Exception => 
        log.error(s"${notify}",e)
        Failure(e)
    }
  }
}
