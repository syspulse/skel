package io.syspulse.skel.video

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.syspulse.skel.Ingestable

case class Video (vid:Video.ID, title:String,ts:Long = System.currentTimeMillis) extends Ingestable {
  override def toLog:String = toString
  override def getKey:Option[Any] = Some(vid.id)
}

object Video {
  type ID = VID
}