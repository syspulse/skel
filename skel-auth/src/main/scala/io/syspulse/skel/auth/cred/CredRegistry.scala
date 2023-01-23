package io.syspulse.skel.auth.cred

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command
import scala.util.Try
import scala.util.Success

object CredRegistry {
  
  final case class CreateCred(req: CredCreateReq, replyTo: ActorRef[Try[Cred]]) extends Command
  final case class GetCred(cid: String, replyTo: ActorRef[Try[Cred]]) extends Command
  final case class GetCreds(replyTo: ActorRef[Try[Creds]]) extends Command
  final case class DeleteCred(cid: String, replyTo: ActorRef[CredActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: CredStore = new CredStoreMem

  def apply(store: CredStore = new CredStoreMem): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: CredStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetCreds(replyTo) =>
        replyTo ! Success(Creds(store.all))
        Behaviors.same

      case CreateCred(req, replyTo) =>
        val cid = Cred(req.cid,req.secret,req.name.getOrElse(""))
        val store1 = store.+(cid)
        replyTo ! Success(cid)
        registry(store1.getOrElse(store))

      case GetCred(cid, replyTo) =>
        replyTo ! store.?(cid)
        Behaviors.same

      case DeleteCred(cid, replyTo) =>
        val store1 = store.del(cid)
        replyTo ! CredActionRes(s"success",Some(cid))
        registry(store1.getOrElse(store))
    }
  }
}

