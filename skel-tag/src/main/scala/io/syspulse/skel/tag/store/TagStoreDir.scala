package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir

import io.syspulse.skel.tag.TagCvs
import io.syspulse.skel.tag.TagCvs._
import io.syspulse.skel.tag._
import io.syspulse.skel.tag.TagJson
import io.syspulse.skel.tag.TagJson._

// Preload from file during start
class TagStoreDir(dir:String = "store/") extends StoreDir[Tag,String](dir) with TagStore {  
  val store = new TagStoreMem

  def all:Seq[Tag] = store.all
  override def limit(from:Option[Int],size:Option[Int]):Seq[Tag] = store.limit(from,size)
  def size:Long = store.size
  override def +(u:Tag):Try[TagStoreDir] = super.+(u).flatMap(_ => store.+(u)).map(_ => this)
  override def del(id:String):Try[TagStoreDir] = super.del(id).flatMap(_ => store.del(id)).map(_ => this)
  override def ?(id:String):Try[Tag] = store.?(id)
  def ??(tags:String,from:Option[Int],size:Option[Int]):Tags = store.??(tags,from,size)
  override def typing(txt:String,from:Option[Int],size:Option[Int]):Tags = store.typing(txt,from,size)
  override def search(txt:String,from:Option[Int],size:Option[Int]):Tags = store.search(txt,from,size)

  // preload
  load(dir)
}