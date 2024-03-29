package io.syspulse.skel.video.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.video._
import io.syspulse.skel.video.VID

class VideoStoreMem extends VideoStore {
  val log = Logger(s"${this}")
  
  var videos: Map[VID,Video] = Map()

  def all:Seq[Video] = videos.values.toSeq

  def size:Long = videos.size

  def +(video:Video):Try[Video] = { 
    videos = videos + (video.vid -> video)
    log.info(s"${video}")
    Success(video)
  }

  def del(vid:VID):Try[VID] = { 
    val sz = videos.size
    videos = videos - vid;
    log.info(s"${vid}")
    if(sz == videos.size) Failure(new Exception(s"not found: ${vid}")) else Success(vid)  
  }

  def ?(vid:VID):Try[Video] = videos.get(vid) match {
    case Some(v) => Success(v)
    case None => Failure(new Exception(s"not found: ${vid}"))
  }

  def ??(txt:String):List[Video] = {
    videos.values.filter(v => {
      v.title.matches(txt)
      //||
      //v.desc.matches(txt)
    }
    ).toList
  }

  def scan(txt:String):List[Video] = ??(txt)
  def search(txt:String):List[Video] = ??(txt + ".*")
  def grep(txt:String):List[Video] = ??(txt)
  def typing(txt:String):List[Video] = ??(txt)
}
