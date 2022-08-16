package io.syspulse.skel.video

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.syspulse.skel.ingest.Ingestable

final case class Videos(users: immutable.Seq[Video])
final case class VideoCreateReq(title:String)
final case class VideoRandomReq()
final case class VideoActionRes(status: String,id:Option[String])

case class Video (vid:Video.ID, title:String,ts:Long = System.currentTimeMillis
) extends Ingestable {
  override def toLog:String = toString
  override def toSimpleLog:String = toCSV
}

object Video {
  type ID = VID
}

