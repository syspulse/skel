package io.syspulse.skel.video

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.syspulse.skel.Ingestable

case class VideoSource (id:VideoSource.ID, name:String, lastTs:Long = 0L) {
  
}

object VideoSource {
  type ID = String
}

