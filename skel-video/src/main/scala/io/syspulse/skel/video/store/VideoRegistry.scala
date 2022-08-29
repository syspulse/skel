package io.syspulse.skel.video.store

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

object VideoRegistry {
  val log = Logger(s"${this}")
  
  final case class GetVideos(replyTo: ActorRef[Videos]) extends Command
  final case class GetVideo(id:VID,replyTo: ActorRef[Option[Video]]) extends Command
  final case class SearchVideo(txt:String,replyTo: ActorRef[Videos]) extends Command
  
  final case class CreateVideo(videoCreate: VideoCreateReq, replyTo: ActorRef[Video]) extends Command
  final case class RandomVideo(replyTo: ActorRef[Video]) extends Command

  final case class DeleteVideo(id: VID, replyTo: ActorRef[VideoActionRes]) extends Command
  
  // this var reference is unfortunately needed for Metrics access
  var store: VideoStore = null //new VideoStoreDB //new VideoStoreCache

  def apply(store: VideoStore = new VideoStoreMem): Behavior[io.syspulse.skel.Command] = {
    this.store = store
    registry(store)
  }

  private def registry(store: VideoStore): Behavior[io.syspulse.skel.Command] = {
    this.store = store

    Behaviors.receiveMessage {
      case GetVideos(replyTo) =>
        replyTo ! Videos(store.all)
        Behaviors.same

      case GetVideo(id, replyTo) =>
        replyTo ! store.?(id)
        Behaviors.same

      case SearchVideo(txt, replyTo) =>
        replyTo ! Videos(store.??(txt))
        Behaviors.same


      case CreateVideo(videoCreate, replyTo) =>
        val vid = VID("M",None,None)
        val video = Video(vid, videoCreate.title, System.currentTimeMillis())
                
        val store1 = store.+(video)

        replyTo ! video
        registry(store1.getOrElse(store))

      case RandomVideo(replyTo) =>
        
        //replyTo ! VideoRandomRes(secret,qrImage)
        Behaviors.same

      
      case DeleteVideo(vid, replyTo) =>
        val store1 = store.del(vid)
        replyTo ! VideoActionRes(s"Success",Some(vid.toString))
        registry(store1.getOrElse(store))
    }
  }
}
