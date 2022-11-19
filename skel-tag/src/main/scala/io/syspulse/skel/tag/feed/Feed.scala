package io.syspulse.skel.tag.feed

import scala.jdk.CollectionConverters._

import scala.util.Random

import io.syspulse.skel.util.Util

import io.syspulse.skel.tag.Tag

class Feed {
  def parse(data:String):Seq[Tag] = {
    data.split("\\n").map(_.trim).filter(_.nonEmpty).flatMap( s => s.split(",").toList match {
      case id :: tags :: score :: Nil => Some(Tag(id,tags = Util.csvToList(tags),score.toDouble))
      case id :: tags :: Nil => Some(Tag(id,tags = Util.csvToList(tags)))
      case _ => None
    })
  }
}
