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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors

object TagRegistry {
  val log = Logger(s"${this}")

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))

  final case class GetTags(from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetTag(ids:Seq[String],replyTo: ActorRef[Tags]) extends Command
  final case class GetSearchFindTag(tags:String,cat:Option[String],from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetSearchTag(tags:String,from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetTypingTag(txt:String,from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command
  final case class GetFindTag(attr:Map[String,String],from:Option[Int],size:Option[Int],replyTo: ActorRef[Tags]) extends Command

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
        store.all(from,size).foreach(tt => replyTo ! Tags(tt, total = Some(tt.size.toLong)))
        Behaviors.same

      case GetTag(ids,replyTo) =>
        store.??(ids).foreach(tt => replyTo ! Tags(tt, Some(tt.size.toLong)))
        Behaviors.same

      case GetSearchFindTag(tags,cat,from,size,replyTo) =>
        replyTo ! store.???(tags,cat,from,size)
        Behaviors.same

      case GetSearchTag(tags,from,size,replyTo) =>
        replyTo ! store.search(tags,from,size)
        Behaviors.same

      case GetTypingTag(txt,from,size,replyTo) =>
        replyTo ! store.typing(txt,from,size)
        Behaviors.same

      case GetFindTag(attr,from,size,replyTo) =>
        val (a,v) = attr.head
        replyTo ! store.find(a,v,from,size)
        Behaviors.same

      case RandomTag(replyTo) =>
        //replyTo ! TagRandomRes(secret,qrImage)
        Behaviors.same

      case CreateTag(req, replyTo) =>
        store.?(req.id).onComplete {
          case Success(_) =>
            replyTo ! Failure(new Exception(s"already exists: ${req.id}"))
          case Failure(_) =>
            val tag = Tag(req.id, ts = System.currentTimeMillis, req.cat, req.tags.map(_.split(";")).flatten)
            store.+(tag).onComplete(replyTo ! _)
        }
        Behaviors.same

      case UpdateTag(id, req, replyTo) =>
        store.!(id, req.cat, req.tags.map(_.map(_.split(";")).flatten)).onComplete(replyTo ! _)
        Behaviors.same

      case DeleteTag(id, replyTo) =>
        store.del(id).onComplete {
          case Success(_) => replyTo ! TagActionRes("200", Some(id))
          case Failure(_) => replyTo ! TagActionRes("619", Some(id))
        }
        Behaviors.same
    }

  }
}
