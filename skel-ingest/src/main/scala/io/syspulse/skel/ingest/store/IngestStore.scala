package io.syspulse.skel.ingest.store

import scala.util.Try
import scala.concurrent.Future
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.ingest._
import io.syspulse.skel.store.Store

abstract class Ing[I] {
  def getId:I
  def searchables():String = ""
}

trait IngestStore[I] extends Store[Ing[I],I] {
  def getKey(i: Ing[I]): I = i.getId
  def +(i:Ing[I]):Future[Ing[I]]
  def del(id:I):Future[I]
  def ?(id:I):Future[Ing[I]]
  def all:Future[Seq[Ing[I]]]
  def size:Future[Long]

  def ??(txt:String):List[Ing[I]]

  def connect[C](config:C):IngestStore[I] = this

  def scan(txt:String):List[Ing[I]]
  def search(txt:String):List[Ing[I]]
  def grep(txt:String):List[Ing[I]]
  def typing(txt:String):List[Ing[I]]
}
