package io.syspulse.skel.video

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger

import akka.stream.scaladsl.Sink

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.ingest.IngestFlow
import io.syspulse.skel.elastic.ElasticFlow

import io.syspulse.skel.video.Video
import io.syspulse.skel.video.VID
import io.syspulse.skel.video.tms._
import io.syspulse.skel.video.VideoJson

class VideoFlowElastic extends ElasticFlow[Video,Video] with VideoFlow {
  override val log = Logger(s"${this}")
  
  import VideoJson._
  implicit val fmt = VideoJson.fmt 

  override def getIndex(d:Video):(String,Video) = (s"${d.title}",d)  
}