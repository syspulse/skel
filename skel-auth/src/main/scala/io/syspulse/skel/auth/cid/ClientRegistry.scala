package io.syspulse.skel.auth.cid

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command
import scala.util.Try
import scala.util.Success

object ClientRegistry {
  
  final case class CreateClient(req: ClientCreateReq, replyTo: ActorRef[Try[Client]]) extends Command
  final case class GetClient(cid: String, replyTo: ActorRef[Try[Client]]) extends Command
  final case class GetClients(replyTo: ActorRef[Try[Clients]]) extends Command
  final case class DeleteClient(cid: String, replyTo: ActorRef[ClientActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: ClientStore = new ClientStoreMem

  def apply(store: ClientStore = new ClientStoreMem): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: ClientStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetClients(replyTo) =>
        replyTo ! Success(Clients(store.all))
        Behaviors.same

      case CreateClient(req, replyTo) =>
        val cid = Client(req.cid,req.secret,req.name.getOrElse(""))
        val store1 = store.+(cid)
        replyTo ! Success(cid)
        registry(store1.getOrElse(store))

      case GetClient(cid, replyTo) =>
        replyTo ! store.?(cid)
        Behaviors.same

      case DeleteClient(cid, replyTo) =>
        val store1 = store.del(cid)
        replyTo ! ClientActionRes(s"success",Some(cid))
        registry(store1.getOrElse(store))
    }
  }
}

