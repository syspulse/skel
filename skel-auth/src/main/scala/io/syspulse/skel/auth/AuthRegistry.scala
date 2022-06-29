package io.syspulse.skel.auth

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command

object AuthRegistry {
  
  final case class GetAuths(replyTo: ActorRef[Auths]) extends Command
  final case class CreateAuth(auth: Auth, replyTo: ActorRef[AuthCreateRes]) extends Command
  final case class GetAuth(auid: String, replyTo: ActorRef[AuthRes]) extends Command
  final case class DeleteAuth(auid: String, replyTo: ActorRef[AuthActionRes]) extends Command

    // this var reference is unfortunately needed for Metrics access
  var store: AuthStore = null //new AuthStoreDB //new AuthStoreCache

  def apply(store: AuthStore = new AuthStoreMem): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: AuthStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetAuths(replyTo) =>
        replyTo ! Auths(store.all)
        Behaviors.same

      case CreateAuth(auth, replyTo) =>
        val store1 = store.+(auth)
        replyTo ! AuthCreateRes(auth)
        registry(store1.getOrElse(store))

      case GetAuth(auid, replyTo) =>
        replyTo ! AuthRes(store.?(auid))
        Behaviors.same

      case DeleteAuth(auid, replyTo) =>
        val store1 = store.del(auid)
        replyTo ! AuthActionRes(s"Success",Some(auid))
        registry(store1.getOrElse(store))
    }
  }
}

