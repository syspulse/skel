package io.syspulse.skel.tag

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable
import io.syspulse.skel.Ingestable

case class Tag (id:String, ts:Long = 0L, cat:String, tags:Seq[String], score:Option[Long] = None, sid:Option[Long] = None) extends Ingestable {
  override def toLog:String = toString
  override def getKey:Option[Any] = Some(id)
}

final case class Tags(tags: immutable.Seq[Tag],total:Option[Long] = None)