package io.syspulse.skel.video.store

import scala.util.{Try, Success, Failure}
import scala.collection.immutable
import scala.concurrent.Future

import com.typesafe.scalalogging.Logger

import io.jvm.uuid._

import io.syspulse.skel.video._
import io.syspulse.skel.video.VID
import io.syspulse.skel.ErrNotFound

class VideoStoreMem extends VideoStore {
  val log = Logger(s"${this}")

  var videos: Map[VID,Video] = Map()

  def all:Future[Seq[Video]] = Future.successful(videos.values.toSeq)

  def size:Future[Long] = Future.successful(videos.size.toLong)

  def +(video:Video):Future[Video] = {
    videos = videos + (video.vid -> video)
    log.info(s"${video}")
    Future.successful(video)
  }

  def del(vid:VID):Future[VID] = {
    val sz = videos.size
    videos = videos - vid
    log.info(s"${vid}")
    if(sz == videos.size) Future.failed(new ErrNotFound(s"${vid}")) else Future.successful(vid)
  }

  def ?(vid:VID):Future[Video] = videos.get(vid) match {
    case Some(v) => Future.successful(v)
    case None => Future.failed(new ErrNotFound(s"${vid}"))
  }

  def ??(txt:String):List[Video] = {
    videos.values.filter(v => {
      v.title.matches(txt)
    }
    ).toList
  }

  def scan(txt:String):List[Video] = ??(txt)
  def search(txt:String):List[Video] = ??(txt + ".*")
  def grep(txt:String):List[Video] = ??(txt)
  def typing(txt:String):List[Video] = ??(txt)
}
