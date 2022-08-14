package io.syspulse.skel.video.elastic

import scala.jdk.CollectionConverters._

import io.syspulse.skel
import io.syspulse.skel.util.Util

import io.syspulse.skel.elastic.ElasticFlow
import io.syspulse.skel.video.Video
import io.syspulse.skel.video.VID
import io.syspulse.skel.video.tms._

class VideoFlow extends ElasticFlow[Video] {
  
  import io.syspulse.skel.video.elastic.VideoElasticJson._
  implicit val fmt = VideoElasticJson.fmt 

  // override def sink():Sink[WriteMessage[Video,NotUsed],Any] = 
  //   Sink.foreach(println _)

  override def parse(data:String):Seq[Video] = TmsParser.fromString(data).map( tms => {
    Video(VID(tms.id),tms.title)
  }).toSeq
  
  override def getIndex(d:Video):(String,Video) = (s"${d.title}",d)  
}