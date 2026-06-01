package io.syspulse.skel.tag.store

import scala.util.Try
import scala.util.{Success,Failure}
import scala.concurrent.{Future, ExecutionContext}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.collection.immutable

import com.typesafe.scalalogging.Logger

import io.syspulse.skel.tag._

class TagStoreMem extends TagStore {
  val log = Logger(s"${this}")

  var tags: Map[String,Tag] = Map()

  def all:Future[Seq[Tag]] = Future.successful(tags.values.toSeq)

  def size:Future[Long] = Future.successful(tags.size)

  def +(tag:Tag):Future[Tag] = {
    // update existing
    // val tag1 = tags.get(tag.id) match {
    //   case Some(tag0) =>
    //     tags = tags + (tag0.id -> tag0.copy(tags = tag0.tags ++ tag.tags))
    //     tag0
    //   case None =>
    //     tags = tags + (tag.id -> tag)
    //     tag
    // }
    tags = tags + (tag.id -> tag)

    log.info(s"add: ${tag}")
    Future.successful(tag)
  }

  def del(id:String):Future[String] = {
    val sz = tags.size
    tags = tags - id
    log.info(s"del: ${id}")
    if(sz == tags.size) Future.failed(new Exception(s"not found: ${id}")) else Future.successful(id)
  }

  def ?(id:String):Future[Tag] = tags.get(id) match {
    case Some(t) => Future.successful(t)
    case None => Future.failed(new Exception(s"not found: ${id}"))
  }

  override def ??(ids:Seq[String])(implicit ec:ExecutionContext):Future[Seq[Tag]] = {
    Future.successful(ids.flatMap(tags.get(_)))
  }

  def search(txt:String,from:Option[Int],size:Option[Int]):Tags = {
    if(txt.trim.size < 3 )
      Tags(Seq())
    else
      ???(".*" + txt + ".*",None,from,size)
  }

  def typing(txt:String,from:Option[Int],size:Option[Int]):Tags = {
    if(txt.trim.size < 3 )
      Tags(Seq())
    else
      ???(txt+".*",None,from,size)
  }

  def ???(tags:String,cat:Option[String],from:Option[Int],size:Option[Int]):Tags = {
    log.info(s"???: ${tags},${cat},${from},${size}")
    val terms = tags.toLowerCase
    val tt =
      this.tags
      .values
      .filter(!cat.isDefined || _.cat.equalsIgnoreCase(cat.get))
      .filter{ t =>
        terms.isEmpty ||
        ( t.id.toLowerCase.matches(terms) ||
         t.tags.filter( tag => tag.toLowerCase.matches(terms)).size > 0
        )
      }
      .toList.sortBy(_.score.map(v => -v))

    Tags(tt.drop(from.getOrElse(0)).take(size.getOrElse(10)),Some(tt.size))
  }

  def !(id:String,cat:Option[String],tags:Option[Seq[String]]):Future[Tag] = {
    log.info(s"update: ${id},${cat},${tags}")
    val t = for {
      t0 <- ?(id).recoverWith { case _ => Future.successful(Tag(id, ts = System.currentTimeMillis, "", Seq())) }
      t1 = if(cat.isDefined) t0.copy(cat = cat.get) else t0
      t2 = if(tags.isDefined) t1.copy(tags = tags.get) else t1
      t3 <- `+`(t2)
    } yield t2
    t
  }

  def find(attr:String,v:Any,from:Option[Int],size:Option[Int]):Tags = {
    log.info(s"attr=(${attr},${v})")
    import io.syspulse.skel.util.Reflect._
    val tt = tags.values.filter(t => t.valueOf[String](attr).map(av => av.toLowerCase.equals(v.toString.toLowerCase)).getOrElse(false)).toSeq

    Tags(tt.drop(from.getOrElse(0)).take(size.getOrElse(10)),Some(tt.size))
  }
}
