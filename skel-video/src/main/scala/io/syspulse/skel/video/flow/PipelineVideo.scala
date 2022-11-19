package io.syspulse.skel.video.flow

import scala.jdk.CollectionConverters._
import scala.concurrent.duration.{Duration,FiniteDuration}
import com.typesafe.scalalogging.Logger

import akka.util.ByteString
import akka.http.javadsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.MediaTypes
import akka.http.scaladsl
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow

import io.syspulse.skel
import io.syspulse.skel.config._
import io.syspulse.skel.util.Util
import io.syspulse.skel.config._

import io.syspulse.skel.ingest._
import io.syspulse.skel.ingest.store._
import io.syspulse.skel.ingest.flow.Pipeline

import spray.json._
import DefaultJsonProtocol._
import java.util.concurrent.TimeUnit

import io.syspulse.skel.video._
import io.syspulse.skel.video.tms._

import VideoJson._

class PipelineVideo(feed:String,output:String)(implicit config:Config)
  extends Pipeline[Video,Video,Video](feed,output,config.throttle,config.delimiter,config.buffer) {

  // import VideoJson._
  // implicit val fmt:JsonFormat[Video] = VideoJson.fmt

  def parse(data:String):Seq[Video] = TmsParser.fromString(data).map( tms => {
    Video(VID(tms.id),tms.title)
  }).toSeq
  
  override def processing:Flow[Video,Video,_] = Flow[Video].map(v => v)

  def transform(v: Video): Seq[Video] = Seq(v)
}
