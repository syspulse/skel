package io.syspulse.skel.video.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

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
  
  def all:Seq[Video] = scan("")

  // slow and memory hungry !
  def size:Long = scan("").size

  def +(yell:Video):Try[VideoStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${yell}"))
  }

  def del(id:ID):Try[VideoStore] = { 
    Failure(new UnsupportedOperationException(s"not implemented: ${id}"))
  }

  def -(yell:Video):Try[VideoStore] = {     
    Failure(new UnsupportedOperationException(s"not implemented: ${yell}"))
  }

  def ?(vid:VID):Option[Video] = searches(vid.toString).headOption

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