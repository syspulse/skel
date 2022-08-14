package io.syspulse.skel.video

import scala.util.Random
import scala.jdk.CollectionConverters._
import scala.collection.immutable

case class VID(id:String) {
  override def toString = s"${id}"
}

object VID {
  def apply(id:String):VID = {
    new VID(id)
  }

  def fromVideo(v:Video):VID = new VID(s"M-${Math.abs(Random.nextInt())}")

  def apply(typ:String,xid:Option[String] = None, episode:Option[Int] = None):VID = {
    val prefix = typ.toUpperCase match {
      case "M" => "M"
      case _ => typ.toUpperCase()
    }

    val id = if(xid.isDefined) xid.get else ""
    val ep = if(episode.isDefined) episode else ""

    new VID(s"${prefix}${xid}${ep}")
  }
}

