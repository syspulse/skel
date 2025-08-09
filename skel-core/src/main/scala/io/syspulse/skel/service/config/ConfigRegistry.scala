package io.syspulse.skel.service.config

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import scala.jdk.CollectionConverters._

import io.syspulse.skel.util.Util
import io.syspulse.skel.config.Configuration
import io.syspulse.skel.Server


final case class Config(k:String,v:String)
final case class Configs(configs: immutable.Seq[Config])

object ConfigRegistry {
  
  sealed trait Command extends io.syspulse.skel.Command

  final case class GetConfigAll(replyTo: ActorRef[Configs]) extends Command
  
  def apply(configuration:Configuration): Behavior[Command] = registry(configuration)

  private def registry(configuration:Configuration): Behavior[Command] =
    Behaviors.receiveMessage {
      
      case GetConfigAll(replyTo) =>
        val insecure = Server.insecure
        val data = if(insecure)
           configuration.getAll().map(kv => Config(kv._1,if(kv._2==null) "" else kv._2.toString))
        else
          Seq.empty[Config]
        
        replyTo ! Configs(data)
        Behaviors.same
    }
}
