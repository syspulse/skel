package io.syspulse.skel.video.store

import scala.collection.immutable
import scala.concurrent.{Future, ExecutionContext}

import io.jvm.uuid._

import io.syspulse.skel.video._
import io.syspulse.skel.store.Store

import io.syspulse.skel.video.Config
import io.syspulse.skel.video.Video
import io.syspulse.skel.video.Video.ID

trait VideoStore extends Store[Video,ID] {
  def getKey(v: Video): ID = v.vid

  def +(video:Video):Future[Video]
  def del(id:ID):Future[ID]
  def ?(id:ID):Future[Video]
  def all:Future[Seq[Video]]
  def size:Future[Long]

  def ??(txt:String):List[Video]

  def scan(txt:String):List[Video]
  def search(txt:String):List[Video]
  def grep(txt:String):List[Video]
  def typing(txt:String):List[Video]
}
