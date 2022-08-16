package io.syspulse.skel.video.file

import scala.jdk.CollectionConverters._

import akka.stream.scaladsl.Sink
import akka.NotUsed

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.video.Video
import io.syspulse.skel.video.VID
import io.syspulse.skel.video.VideoFlow

import io.syspulse.skel.ingest.IngestFlow

class VideoFlowFile(file:String) extends VideoFlow with IngestFlow[Video,Video]{
  
  override def transform(t: Video): Video = t
  override def sink():Sink[Video,Any] = IngestFlow.toHiveFile(file)
}