package io.syspulse.skel.video

import scala.jdk.CollectionConverters._
import com.typesafe.scalalogging.Logger

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.ingest.IngestFlow

import io.syspulse.skel.video.Video
import io.syspulse.skel.video.VID
import io.syspulse.skel.video.tms._
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Flow

trait VideoFlow {
  protected val log = Logger(s"${this}")

  def flow:Flow[Video,Video,_] = Flow[Video].map(v => v)

  def parse(data:String):Seq[Video] = TmsParser.fromString(data).map( tms => {
    Video(VID(tms.id),tms.title)
  }).toSeq
  
}