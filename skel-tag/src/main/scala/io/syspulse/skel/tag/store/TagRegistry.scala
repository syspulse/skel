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
import scala.util.Success
import scala.util.Failure

object TagRegistry {
  val log = Logger(s"${this}")
  
  final case class GetTags(from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetTag(id:String,replyTo: ActorRef[Try[Tag]]) extends Command
  final case class GetSearchTag(tags:String,from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetTypingTag(txt:String,from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class RandomTag(replyTo: ActorRef[Tag]) extends Command
  final case class CreateTag(req: TagCreateReq, replyTo: ActorRef[Try[Tag]]) extends Command
  final case class UpdateTag(id: String,req: TagUpdateReq, replyTo: ActorRef[Try[Tag]]) extends Command
  final case class DeleteTag(id: String,replyTo: ActorRef[TagActionRes]) extends Command

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
        replyTo ! Tags(store.all(from,size),total = Some(store.size))
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

      case CreateTag(req, replyTo) =>
        store.?(req.id) match {
          case Success(_) => 
            replyTo ! Failure(new Exception(s"already exists: ${req.id}"))
            Success(store)
          case _ =>  
            val tag = Tag(req.id, ts = System.currentTimeMillis, req.cat, req.tags.map(_.split(";")).flatten)
            val r = store.+(tag)
            replyTo ! r.map(_ => tag)
            Success(store)
        }        
        Behaviors.same

      case UpdateTag(id, req, replyTo) =>
        val tag = store.!(id,req.cat,req.tags.map(_.map(_.split(";")).flatten))

        replyTo ! tag
        Behaviors.same
      
      case DeleteTag(id, replyTo) =>
        val store1 = store.del(id)
        replyTo ! TagActionRes(s"Success",Some(id))
        Behaviors.same
    }
    
  }
}
