package io.syspulse.skel.auth

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._

final case class Auths(auths: immutable.Seq[Auth])

object AuthRegistry {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetAuths(replyTo: ActorRef[Auths]) extends Command
  final case class CreateAuth(auth: Auth, replyTo: ActorRef[ActionPerformed]) extends Command
  final case class GetAuth(auid: String, replyTo: ActorRef[GetAuthResponse]) extends Command
  final case class DeleteAuth(auid: String, replyTo: ActorRef[ActionPerformed]) extends Command

  final case class GetAuthResponse(maybeAuth: Option[Auth])
  final case class ActionPerformed(description: String,code:Option[String])

  def apply(): Behavior[io.syspulse.skel.Command] = registry(Set.empty)

  private def registry(auths: Set[Auth]): Behavior[io.syspulse.skel.Command] =
    Behaviors.receiveMessage {
      case GetAuths(replyTo) =>
        replyTo ! Auths(auths.toSeq)
        Behaviors.same
      case CreateAuth(auth, replyTo) =>
        replyTo ! ActionPerformed(s"created",Some(auth.idToken))
        registry(auths + auth)
      case GetAuth(auid, replyTo) =>
        replyTo ! GetAuthResponse(auths.find(_.auid == auid))
        Behaviors.same
      case DeleteAuth(auid, replyTo) =>
        replyTo ! ActionPerformed(s"deleted",Some(auid))
        registry(auths.filterNot(_.auid == auid))
    }
}

