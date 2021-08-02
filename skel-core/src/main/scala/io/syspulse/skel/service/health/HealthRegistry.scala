package io.syspulse.skel.service.health

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import scala.jdk.CollectionConverters._

final case class Health(status:String)

import io.syspulse.skel.util.Util

object HealthRegistry {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetHealth(replyTo: ActorRef[Health]) extends Command
  
  def apply(): Behavior[Command] = registry(Health("OK"))

  private def registry(health:Health): Behavior[Command] =
    Behaviors.receiveMessage {
      
      case GetHealth(replyTo) =>
        replyTo ! Health("OK")
        Behaviors.same
    }
}
