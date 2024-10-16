package io.syspulse.skel.video.file

import scala.jdk.CollectionConverters._

import akka.stream.scaladsl.Sink
import akka.NotUsed
import akka.stream.scaladsl.Flow

import io.syspulse.skel
import io.syspulse.skel.util.Util
import io.syspulse.skel.video.Video
import io.syspulse.skel.video.VID
import io.syspulse.skel.video.flow.VideoFlow
import io.syspulse.skel.video.VideoJson

import io.syspulse.skel.ingest.IngestFlow
import io.syspulse.skel.ingest.flow.Flows


class VideoFlowFile(file:String) extends VideoFlow with IngestFlow[Video,Video,Video]{
  import VideoJson._

  def transform(t: Video): Seq[Video] = Seq(t)
  def shaping:Flow[Video,Video,_] = Flow[Video].map(i => i)

  override def sink():Sink[Video,Any] = {
    log.info(s"writing -> ${file}")
    Flows.toHiveFileSize(file)
  }
}