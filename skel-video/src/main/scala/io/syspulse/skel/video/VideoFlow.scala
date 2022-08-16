package io.syspulse.skel.video

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.ingest.IngestFlow

import io.syspulse.skel.video.Video
import io.syspulse.skel.video.VID
import io.syspulse.skel.video.tms._
import akka.stream.scaladsl.Sink

trait VideoFlow {

  def parse(data:String):Seq[Video] = TmsParser.fromString(data).map( tms => {
    Video(VID(tms.id),tms.title)
  }).toSeq
  
}