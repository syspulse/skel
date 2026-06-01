package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.store.StoreDir
import io.syspulse.skel.store.Store

import io.syspulse.skel.tag.TagCsv
import io.syspulse.skel.tag.TagCsv._
import io.syspulse.skel.tag._
import io.syspulse.skel.tag.TagJson
import io.syspulse.skel.tag.TagJson._

// Preload from file during start
class TagStoreDir(dir:String = "store/") extends StoreDir[Tag,String](dir) with TagStore {
  val store = new TagStoreMem

  def toKey(id:String):String = id
  def all:Future[Seq[Tag]] = store.all

  override def all(from:Option[Int],size:Option[Int]):Future[Seq[Tag]] = store.all(from,size)
  def size:Future[Long] = store.size
  override def +(u:Tag):Future[Tag] = super.+(u).flatMap(_ => store.+(u))
  override def del(id:String):Future[String] = {
    store.del(id).flatMap(_ => super.del(id))
  }
  override def ?(id:String):Future[Tag] = store.?(id)

  override def ??(ids:Seq[String])(implicit ec:ExecutionContext):Future[Seq[Tag]] = store.??(ids)(ec)

  def ???(tags:String,cat:Option[String],from:Option[Int],size:Option[Int]):Tags = store.???(tags,cat,from,size)
  override def typing(txt:String,from:Option[Int],size:Option[Int]):Tags = store.typing(txt,from,size)
  override def search(txt:String,from:Option[Int],size:Option[Int]):Tags = store.search(txt,from,size)

  override def !(id:String,cat:Option[String],tags:Option[Seq[String]]):Future[Tag] =
    store.!(id,cat,tags).flatMap(t => Store.toFuture(this.writeFile(t)))

  override def find(attr:String,v:Any,from:Option[Int],size:Option[Int]):Tags = store.find(attr,v,from,size)

  // preload
  load(dir)
  // watch
  watch(dir)
}
