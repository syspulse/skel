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
  
  final case class GetNotifys(replyTo: ActorRef[Notifys]) extends Command
  final case class GetNotify(id:UUID,replyTo: ActorRef[Option[Notify]]) extends Command
  final case class GetNotifyByEid(eid:String,replyTo: ActorRef[Option[Notify]]) extends Command
  
  final case class CreateNotify(notifyCreate: NotifyCreateReq, replyTo: ActorRef[Notify]) extends Command
  final case class RandomNotify(replyTo: ActorRef[Notify]) extends Command

  final case class DeleteNotify(id: UUID, replyTo: ActorRef[NotifyActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: NotifyStore = null //new NotifyStoreDB //new NotifyStoreCache

  def apply(store: NotifyStore = new NotifyStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: NotifyStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetNotifys(replyTo) =>
        replyTo ! Notifys(store.all)
        Behaviors.same

      case GetNotify(id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case GetNotifyByEid(eid, replyTo) =>
        replyTo ! store.findByEid(eid)
        Behaviors.same


      case CreateNotify(notifyCreate, replyTo) =>
        val id = notifyCreate.uid.getOrElse(UUID.randomUUID())

        val notify = Notify(id, notifyCreate.email, notifyCreate.name, notifyCreate.eid, System.currentTimeMillis())
        val store1 = store.+(notify)

        replyTo ! notify
        registry(store1.getOrElse(store))

      case RandomNotify(replyTo) =>
        
        //replyTo ! NotifyRandomRes(secret,qrImage)
        Behaviors.same

      
      case DeleteNotify(id, replyTo) =>
        val store1 = store.del(id)
        replyTo ! NotifyActionRes(s"Success",Some(id))
        registry(store1.getOrElse(store))
    }
  }
}
