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

  def apply(): Behavior[Command] = registry(Set.empty)

  private def registry(auths: Set[Auth]): Behavior[Command] =
    Behaviors.receiveMessage {
      case GetAuths(replyTo) =>
        replyTo ! Auths(auths.toSeq)
        Behaviors.same
      case CreateAuth(auth, replyTo) =>
        replyTo ! CreateAuthRsp(auth)
        registry(auths + auth)
      case GetAuth(auid, replyTo) =>
        replyTo ! GetAuthRsp(auths.find(_.auid == auid))
        Behaviors.same
      case DeleteAuth(auid, replyTo) =>
        replyTo ! ActionRsp(s"deleted",Some(auid))
        registry(auths.filterNot(_.auid == auid))
    }
}

