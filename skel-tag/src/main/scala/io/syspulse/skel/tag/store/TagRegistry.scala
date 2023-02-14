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
import scala.util.Try

object TagRegistry {
  val log = Logger(s"${this}")
  
  final case class GetTags(from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetTag(id:String,replyTo: ActorRef[Try[Tag]]) extends Command
  final case class GetSearchTag(tags:String,from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetTypingTag(txt:String,from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
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
      case GetTags(from,size,replyTo) =>
        replyTo ! Tags(store.limit(from,size),total = Some(store.size))
        Behaviors.same

      case GetTag(id,replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case GetSearchTag(tags,from,size,replyTo) =>
        replyTo ! store.search(tags,from,size)
        Behaviors.same

      case GetTypingTag(txt,from,size,replyTo) =>
        replyTo ! store.typing(txt,from,size)
        Behaviors.same
      
      case RandomTag(replyTo) =>        
        //replyTo ! TagRandomRes(secret,qrImage)
        Behaviors.same      
    }
  }
}
