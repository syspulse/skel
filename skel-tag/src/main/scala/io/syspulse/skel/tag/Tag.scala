package io.syspulse.skel.tag

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.syspulse.skel.Ingestable

case class Tag (id:String, tags:List[String], score:Double = 0.0) extends Ingestable {
  override def toLog:String = toString
  override def getKey:Option[Any] = Some(id)
}

