package io.syspulse.skel.tag.store

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.tag._

import io.syspulse.skel.tag.server._

object TagRegistry {
  val log = Logger(s"${this}")
  
  final case class GetTags(replyTo: ActorRef[Tags]) extends Command
  final case class GetTag(tags:String,replyTo: ActorRef[Tags]) extends Command
  final case class RandomTag(replyTo: ActorRef[Tag]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: TagStore = null //new TagStoreDB //new TagStoreCache

  def apply(store: TagStore = new TagStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: TagStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetTags(replyTo) =>
        replyTo ! Tags(store.all)
        Behaviors.same

      case GetTag(tags, replyTo) =>
        replyTo ! Tags(store.?(tags))
        Behaviors.same
      
      case RandomTag(replyTo) =>        
        //replyTo ! TagRandomRes(secret,qrImage)
        Behaviors.same      
    }
  }
}