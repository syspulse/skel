package io.syspulse.skel.video.store

import scala.util.{Try, Success, Failure}

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import scala.collection.immutable
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.Command

import io.syspulse.skel.video._
import io.syspulse.skel.video.Video.ID
import io.syspulse.skel.video.server._

import scala.concurrent.Future
import scala.concurrent.ExecutionContextExecutor
import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext

object VideoRegistry {
  val log = Logger(s"${this}")

  final case class GetVideos(replyTo: ActorRef[Videos]) extends Command
  final case class GetVideo(id:VID,replyTo: ActorRef[Try[Video]]) extends Command
  final case class SearchVideo(txt:String,replyTo: ActorRef[Videos]) extends Command
  final case class TypingVideo(txt:String,replyTo: ActorRef[Videos]) extends Command

  final case class CreateVideo(videoCreate: VideoCreateReq, replyTo: ActorRef[Video]) extends Command
  final case class RandomVideo(replyTo: ActorRef[Video]) extends Command

  final case class DeleteVideo(id: VID, replyTo: ActorRef[VideoActionRes]) extends Command

  // this var reference is unfortunately needed for Metrics access
  var store: VideoStore = null //new VideoStoreDB //new VideoStoreCache

  implicit val ec: ExecutionContextExecutor = {
    ExecutionContext.fromExecutor(Executors.newFixedThreadPool(16))
  }

  def apply(store: VideoStore = new VideoStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: VideoStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetVideos(replyTo) =>
        store.all.foreach(videos => replyTo ! Videos(videos.toList))
        Behaviors.same

      case GetVideo(id, replyTo) =>
        store.?(id).onComplete(replyTo ! _)
        Behaviors.same

      case SearchVideo(txt, replyTo) =>
        replyTo ! Videos(store.??(txt))
        Behaviors.same

      case TypingVideo(txt, replyTo) =>
        replyTo ! Videos(store.typing(txt))
        Behaviors.same

      case CreateVideo(videoCreate, replyTo) =>
        val vid = VID("M",None,None)
        val video = Video(vid, videoCreate.title, ts = System.currentTimeMillis())

        store.+(video).foreach(_ => replyTo ! video)
        Behaviors.same

      case RandomVideo(replyTo) =>
        //replyTo ! VideoRandomRes(secret,qrImage)
        Behaviors.same

      case DeleteVideo(vid, replyTo) =>
        store.del(vid).onComplete {
          case Success(_) => replyTo ! VideoActionRes("200", Some(vid.toString))
          case Failure(_) => replyTo ! VideoActionRes("619", Some(vid.toString))
        }
        Behaviors.same
    }
  }
}
