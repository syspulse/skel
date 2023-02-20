package io.syspulse.skel.notify.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.notify._

object NotifyRegistry {
  val log = Logger(s"${this}")
  
  final case class CreateNotify(notifyCreate: NotifyReq, replyTo: ActorRef[Notify]) extends Command
  
  final case class DeleteNotify(id: UUID, replyTo: ActorRef[NotifyActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: NotifyStore = null

  def apply(store: NotifyStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: NotifyStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {

      case CreateNotify(notifyReq, replyTo) =>
        log.info(s"${notifyReq}")
        val notify = Notify(
          notifyReq.to,
          notifyReq.subj, 
          notifyReq.msg, 
          System.currentTimeMillis(),
          severity = notifyReq.severity,
          scope = notifyReq.scope
        )
        
        val store1 = store.+(notify)

        replyTo ! notify
        registry(store1.getOrElse(store))
    }
  }
}
