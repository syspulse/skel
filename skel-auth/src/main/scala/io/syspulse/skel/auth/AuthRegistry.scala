package io.syspulse.skel.auth

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command

final case class Auths(auths: immutable.Seq[Auth])

object AuthRegistry {
  
  final case class GetAuths(replyTo: ActorRef[Auths]) extends Command
  final case class CreateAuth(auth: Auth, replyTo: ActorRef[CreateAuthRsp]) extends Command
  final case class GetAuth(auid: String, replyTo: ActorRef[GetAuthRsp]) extends Command
  final case class DeleteAuth(auid: String, replyTo: ActorRef[ActionRsp]) extends Command

  final case class GetAuthRsp(auth: Option[Auth])
  final case class CreateAuthRsp(auth: Auth)
  final case class ActionRsp(description: String,code:Option[String])

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
        replyTo ! Auths(store.getAll)
        Behaviors.same
      case CreateAuth(auth, replyTo) =>
        
        val store1 = store.+(auth)
        replyTo ! CreateAuthRsp(auth)
        registry(store1.getOrElse(store))

      case GetAuth(auid, replyTo) =>
      
        replyTo ! GetAuthRsp(store.get(auid))
        Behaviors.same

      case DeleteAuth(auid, replyTo) =>
        val store1 = store.del(auid)
        replyTo ! ActionRsp(s"deleted",Some(auid))
        registry(store1.getOrElse(store))
    }
  }
}

