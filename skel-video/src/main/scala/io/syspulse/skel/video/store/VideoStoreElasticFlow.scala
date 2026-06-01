package io.syspulse.skel.video.store

import scala.util.{Try, Success, Failure}
import scala.collection.immutable
import scala.concurrent.Future

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.video._
import io.syspulse.skel.video.Video.ID

import io.syspulse.skel.video._
import io.syspulse.skel.video.elastic.VideoScan
import io.syspulse.skel.video.elastic.VideoSearch

// Datastore base on Akka Flow ElasticClient
class VideoStoreElasticFlow(elasticUri:String,elacticIndex:String) extends VideoFlowElastic with VideoScan with VideoSearch with VideoStore {
  override val log = Logger(s"${this}")

  import io.syspulse.skel.video.elastic.VideoElasticJson
  import io.syspulse.skel.video.elastic.VideoElasticJson._
  override implicit val fmt = VideoJson.fmt

  connect(elasticUri,elacticIndex)

  def all:Future[Seq[Video]] = Future.successful(scan(""))

  // slow and memory hungry !
  def size:Future[Long] = Future.successful(scan("").size.toLong)

  def +(video:Video):Future[Video] = {
    Future.failed(new UnsupportedOperationException(s"not implemented: ${video}"))
  }

  def del(id:ID):Future[ID] = {
    Future.failed(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def ?(vid:VID):Future[Video] = searches(vid.toString).take(1).headOption match {
    case Some(o) => Future.successful(o)
    case None => Future.failed(new Exception(s"not found: ${vid}"))
  }

  def ??(txt:String):List[Video] = {
    searches(txt).toList
  }

  override def scan(txt:String):List[Video] = super.scan(txt).toList
  override def search(txt:String):List[Video] = super.searches(txt).toList
  override def grep(txt:String):List[Video] = super.grep(txt).toList
  override def typing(txt:String):List[Video] = {
    super.typing(txt).toList
  }
}
