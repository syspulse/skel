package io.syspulse.skel.ingest.store

import scala.util.Try
import scala.collection.immutable
import io.jvm.uuid._

import io.syspulse.skel.ingest._
import io.syspulse.skel.store.Store

abstract class IN[ID] {
  def id:ID
  def searchables():String = ""
}

trait IngestStore[IN,ID] extends Store[IN,ID] {
  
  def +(yell:IN):Try[IngestStore[IN,ID]]
  def -(yell:IN):Try[IngestStore[IN,ID]]
  def del(id:ID):Try[IngestStore[IN,ID]]
  def ?(id:ID):Try[IN]
  def all:Seq[IN]
  def size:Long

  def ??(txt:String):List[IN]

  def connect[C](config:C):IngestStore[IN,ID] = this

  def scan(txt:String):List[IN]
  def search(txt:String):List[IN]
  def grep(txt:String):List[IN]
  def typing(txt:String):List[IN]
}
