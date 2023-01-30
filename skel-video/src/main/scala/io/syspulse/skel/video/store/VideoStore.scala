package io.syspulse.skel.video.store

import scala.util.Try

import scala.collection.immutable

import io.jvm.uuid._

import io.syspulse.skel.video._
import io.syspulse.skel.store.Store

import io.syspulse.skel.video.Config
import io.syspulse.skel.video.Video
import io.syspulse.skel.video.Video.ID

trait VideoStore extends Store[Video,ID] {
  def getKey(v: Video): ID = v.vid  

  def +(video:Video):Try[VideoStore]
  def del(id:ID):Try[VideoStore]
  def ?(id:ID):Try[Video]
  def all:Seq[Video]
  def size:Long

  def ??(txt:String):List[Video]

  // def connect(config:Config):VideoStore = this

  def scan(txt:String):List[Video]
  def search(txt:String):List[Video]
  def grep(txt:String):List[Video]
  def typing(txt:String):List[Video]
}
