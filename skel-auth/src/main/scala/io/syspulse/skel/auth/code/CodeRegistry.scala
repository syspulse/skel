package io.syspulse.skel.auth.code

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command
import scala.util.Try
import scala.util.Success

object CodeRegistry {
  
  final case class CreateCode(code: Code, replyTo: ActorRef[CodeCreateRes]) extends Command
  final case class UpdateCode(code: Code, replyTo: ActorRef[CodeCreateRes]) extends Command
  final case class GetCode(code: String, replyTo: ActorRef[Try[Code]]) extends Command
  final case class GetCodeByToken(code: String, replyTo: ActorRef[CodeRes]) extends Command
  final case class GetCodes(replyTo: ActorRef[Try[Codes]]) extends Command
  final case class DeleteCode(code: String, replyTo: ActorRef[CodeActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: CodeStore = new CodeStoreMem

  def apply(store: CodeStore = new CodeStoreMem): Behavior[Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: CodeStore): Behavior[Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetCodes(replyTo) =>
        replyTo ! Success(Codes(store.all))
        Behaviors.same

      case CreateCode(code, replyTo) =>
        val store1 = store.+(code)
        replyTo ! CodeCreateRes(code)
        Behaviors.same

      case UpdateCode(code, replyTo) =>
        val store1 = store.!(code)
        replyTo ! CodeCreateRes(code)
        Behaviors.same

      case GetCode(code, replyTo) =>
        replyTo ! store.?(code)
        Behaviors.same

      case GetCodeByToken(accessToken, replyTo) =>
        replyTo ! CodeRes(store.getByToken(accessToken))
        Behaviors.same

      case DeleteCode(code, replyTo) =>
        val store1 = store.del(code)
        replyTo ! CodeActionRes(s"Success",Some(code))
        Behaviors.same
    }
  }
}

