package io.syspulse.skel.auth.code

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable

import io.jvm.uuid._
import io.syspulse.skel.Command

object CodeRegistry {
 
  final case class CreateCode(code: Code, replyTo: ActorRef[CreateCodeRsp]) extends Command
  final case class GetCode(auid: String, replyTo: ActorRef[GetCodeRsp]) extends Command
  final case class DeleteCode(auid: String, replyTo: ActorRef[ActionRsp]) extends Command

  final case class GetCodeRsp(code: Option[Code])
  final case class CreateCodeRsp(code: Code)

  final case class ActionRsp(description: String,code:Option[String])

  def apply(): Behavior[Command] = registry(Map.empty)

  private def registry(codes: Map[String,Code]): Behavior[Command] =
    Behaviors.receiveMessage {
      case CreateCode(code, replyTo) =>
        replyTo ! CreateCodeRsp(code)
        registry( codes + (code.authCode -> code))
      case GetCode(authCode, replyTo) =>
        replyTo ! GetCodeRsp(codes.get(authCode))
        Behaviors.same
      case DeleteCode(authCode, replyTo) =>
        val code = codes.get(authCode)
        if(code.isDefined) {
          replyTo ! ActionRsp(s"deleted",code.map(_.authCode))
          registry(codes.-(authCode))
        } else
          Behaviors.same
    }
}

